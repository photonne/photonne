using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Memories;

public class MemoriesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/memories", Handle)
            .WithName("GetMemories")
            .WithTags("Assets")
            .WithDescription("Returns assets from the same day in previous years")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        [FromQuery] bool? test,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        // "On this day" is a LOCAL-calendar concept: capture dates are stored as
        // the photo's local wall-clock, so compare against the user's local
        // today (from the configured timezone), not UtcNow — otherwise photos
        // near midnight, and the whole first offset-hours of the local day, fall
        // onto the wrong day.
        var tz = await MetadataTimeZone.ResolveAsync(settingsService, ct);
        var today = MetadataTimeZone.LocalNow(tz);
        var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, $"/assets/users/{username}", ct);

        if (test == true)
        {
            var testAssets = await dbContext.Assets
                .Include(a => a.Exif)
                .Include(a => a.Thumbnails)
                .Include(a => a.Tags)
                .Include(a => a.UserTags)
                    .ThenInclude(ut => ut.UserTag)
                .Where(a => a.DeletedAt == null && !a.IsFileMissing
                         && a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value))
                .OrderBy(_ => EF.Functions.Random())
                .Take(15)
                .ToListAsync(ct);

            return Results.Ok(testAssets.Select(a => MapToResponse(a)).ToList());
        }

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a =>
                a.DeletedAt == null &&
                !a.IsFileMissing &&
                a.CapturedAt.Month == today.Month &&
                a.CapturedAt.Day == today.Day &&
                a.CapturedAt.Year < today.Year &&
                a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value));

        var assets = await query
            .OrderByDescending(a => a.CapturedAt)
            .Take(100)
            .ToListAsync(ct);

        return Results.Ok(assets.Select(MapToResponse).ToList());
    }

    private static TimelineResponse MapToResponse(Asset a) => new()
    {
        Id = a.Id,
        FileName = a.FileName,
        FullPath = a.FullPath,
        FileSize = a.FileSize,
        FileCreatedAt = a.CapturedAt,
        FileModifiedAt = a.FileModifiedAt,
        Extension = a.Extension,
        ScannedAt = a.ScannedAt,
        Type = a.Type.ToString(),
        Checksum = a.Checksum,
        HasExif = a.Exif != null,
        HasThumbnails = a.Thumbnails.Any(),
        SyncStatus = AssetSyncStatus.Synced,
        Width = a.Exif?.Width,
        Height = a.Exif?.Height,
        IsFavorite = a.IsFavorite,
        IsReadOnly = a.ExternalLibraryId.HasValue,
        Tags = BuildTagList(a)
    };

    private static List<string> BuildTagList(Asset asset)
    {
        var autoTags = asset.Tags.Select(t => t.TagType.ToString());
        var userTags = asset.UserTags.Select(t => t.UserTag.Name);
        return autoTags.Concat(userTags)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(t => t)
            .ToList();
    }

}
