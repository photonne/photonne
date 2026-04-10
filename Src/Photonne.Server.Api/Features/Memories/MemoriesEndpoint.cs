using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

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
        ClaimsPrincipal user,
        [FromQuery] bool? test,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        var isAdmin = user.IsInRole("Admin");
        var today = DateTime.UtcNow;

        if (test == true)
        {
            var testQuery = dbContext.Assets
                .Include(a => a.Exif)
                .Include(a => a.Thumbnails)
                .Include(a => a.Tags)
                .Include(a => a.UserTags)
                    .ThenInclude(ut => ut.UserTag)
                .Where(a => a.DeletedAt == null);

            if (!isAdmin)
            {
                var allowedIds = await GetAllowedFolderIdsAsync(dbContext, userId, ct);
                testQuery = testQuery.Where(a => a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value));
            }

            var testAssets = await testQuery
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
                a.FileCreatedAt.Month == today.Month &&
                a.FileCreatedAt.Day == today.Day &&
                a.FileCreatedAt.Year < today.Year);

        if (!isAdmin)
        {
            var allowedIds = await GetAllowedFolderIdsAsync(dbContext, userId, ct);
            query = query.Where(a => a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value));
        }

        var assets = await query
            .OrderByDescending(a => a.FileCreatedAt)
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
        FileCreatedAt = a.FileCreatedAt,
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

    private static async Task<HashSet<Guid>> GetAllowedFolderIdsAsync(
        ApplicationDbContext dbContext, Guid userId, CancellationToken ct)
    {
        var userRootPath = $"/assets/users/{userId}";
        var allFolders = await dbContext.Folders.ToListAsync(ct);
        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .ToListAsync(ct);
        var foldersWithPermissions = await dbContext.FolderPermissions
            .Select(p => p.FolderId).Distinct().ToHashSetAsync(ct);

        var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();
        foreach (var f in allFolders)
        {
            if (!foldersWithPermissions.Contains(f.Id) &&
                f.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
                allowedIds.Add(f.Id);
        }

        // Carpetas de bibliotecas externas accesibles
        var accessibleLibraryIds = await dbContext.ExternalLibraryPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .Select(p => p.ExternalLibraryId)
            .ToHashSetAsync(ct);

        foreach (var f in allFolders)
        {
            if (f.ExternalLibraryId.HasValue && accessibleLibraryIds.Contains(f.ExternalLibraryId.Value))
                allowedIds.Add(f.Id);
        }

        return allowedIds;
    }
}
