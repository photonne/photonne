using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Assets;

/// <summary>
/// Paginated listing of assets the caller has moved to the trash. Mirrors
/// <c>ArchiveEndpoint.GetArchived</c> in shape: cursor-based, returns
/// <c>{ items, hasMore, nextCursor }</c>. The trash listing is built from
/// <c>DeletedAt</c> (set when an asset is "soft-deleted" via <c>POST
/// /api/assets/delete</c>) rather than the legacy folder-walk approach in
/// the web client.
/// </summary>
public class TrashListEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/trash", Handle)
            .WithName("GetTrashedAssets")
            .WithTags("Assets")
            .WithDescription("Lists assets the user has soft-deleted (in trash)")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int pageSize,
        CancellationToken ct)
    {
        if (pageSize <= 0) pageSize = 150;
        if (pageSize > 500) pageSize = 500;

        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var isAdmin = user.IsInRole("Admin");

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt != null);

        if (!isAdmin)
        {
            // Trash always lives under the user's root, so a prefix check on
            // the virtual path is enough — no need to chase folder
            // permissions like Archive does.
            var virtualRoot = $"/assets/users/{userId}";
            query = query.Where(a => a.FullPath.StartsWith(virtualRoot));
        }

        // We page by DeletedAt so the user sees the most recently trashed
        // items first regardless of the file's original creation date.
        if (cursor.HasValue)
        {
            var cursorUtc = cursor.Value.ToUniversalTime();
            query = query.Where(a => a.DeletedAt < cursorUtc);
        }

        var dbItems = await query
            .OrderByDescending(a => a.DeletedAt)
            .ThenBy(a => a.Id)
            .Take(pageSize + 1)
            .ToListAsync(ct);

        var hasMore = dbItems.Count > pageSize;
        var assets = hasMore ? dbItems.Take(pageSize).ToList() : dbItems;

        var items = assets.Select(asset => new TimelineResponse
        {
            Id = asset.Id,
            FileName = asset.FileName,
            FullPath = asset.FullPath,
            FileSize = asset.FileSize,
            FileCreatedAt = asset.FileCreatedAt,
            FileModifiedAt = asset.FileModifiedAt,
            Extension = asset.Extension,
            ScannedAt = asset.ScannedAt,
            Type = asset.Type.ToString(),
            Checksum = asset.Checksum,
            HasExif = asset.Exif != null,
            HasThumbnails = asset.Thumbnails.Any(),
            SyncStatus = AssetSyncStatus.Synced,
            Width = asset.Exif?.Width,
            Height = asset.Exif?.Height,
            IsFavorite = asset.IsFavorite,
            IsArchived = asset.IsArchived,
            Tags = BuildTagList(asset),
            IsReadOnly = asset.ExternalLibraryId.HasValue
        }).ToList();

        var nextCursor = hasMore ? assets.Last().DeletedAt : (DateTime?)null;

        return Results.Ok(new
        {
            Items = items,
            HasMore = hasMore,
            NextCursor = nextCursor
        });
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        userId = Guid.Empty;
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return claim != null && Guid.TryParse(claim.Value, out userId);
    }

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
