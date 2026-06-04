using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Utilities;

public class MyDuplicatesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/utilities/duplicates", Handle)
            .WithName("GetMyDuplicates")
            .WithTags("Utilities")
            .WithDescription("Returns duplicate asset groups for the current user's accessible assets.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        ApplicationDbContext dbContext,
        AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var userRootPath = $"/assets/users/{username}";
        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && !a.IsArchived && !string.IsNullOrEmpty(a.Checksum)
                     && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));

        var assets = await query.ToListAsync(cancellationToken);

        var groups = assets
            .GroupBy(a => a.Checksum)
            .Where(g => g.Count() > 1)
            .Select(g => new UserDuplicateGroupResponse
            {
                Hash = g.Key,
                TotalSize = g.Sum(a => a.FileSize),
                Assets = g.Select(a => MapToResponse(a)).ToList()
            })
            .OrderByDescending(g => g.TotalSize)
            .ToList();

        return Results.Ok(groups);
    }

    private static TimelineResponse MapToResponse(Shared.Models.Asset a) => new()
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
        Tags = BuildTagList(a),
        IsFavorite = a.IsFavorite,
        IsArchived = a.IsArchived
    };

    private static List<string> BuildTagList(Shared.Models.Asset asset)
    {
        var autoTags = asset.Tags.Select(t => t.TagType.ToString());
        var userTags = asset.UserTags.Select(t => t.UserTag.Name);
        return autoTags.Concat(userTags)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(t => t)
            .ToList();
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}
