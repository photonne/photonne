using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Dtos;

namespace Photonne.Server.Api.Features.Favorites;

public class FavoritesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/favorites", Handle)
            .WithName("GetFavorites")
            .WithTags("Assets")
            .WithDescription("Gets all favorite assets for the current user")
            .RequireAuthorization();
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int pageSize,
        CancellationToken cancellationToken)
    {
        if (pageSize <= 0) pageSize = 150;
        if (pageSize > 500) pageSize = 500;

        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        var isAdmin = user.IsInRole("Admin");
        var userRootPath = $"/assets/users/{userId}";

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
            .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && !a.IsArchived && a.IsFavorite);

        if (!isAdmin)
        {
            var allFolders = await dbContext.Folders.ToListAsync(cancellationToken);
            var permissions = await dbContext.FolderPermissions
                .Where(p => p.UserId == userId && p.CanRead)
                .ToListAsync(cancellationToken);

            var foldersWithPermissionsSet = await dbContext.FolderPermissions
                .Select(p => p.FolderId)
                .Distinct()
                .ToHashSetAsync(cancellationToken);

            var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();
            foreach (var folder in allFolders)
            {
                if (!foldersWithPermissionsSet.Contains(folder.Id) &&
                    folder.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
                {
                    allowedIds.Add(folder.Id);
                }
            }

            query = query.Where(a => a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value));
        }

        if (cursor.HasValue)
            query = query.Where(a => a.CreatedDate < cursor.Value.ToUniversalTime());

        var dbItems = await query
            .OrderByDescending(a => a.CreatedDate)
            .ThenByDescending(a => a.ModifiedDate)
            .Take(pageSize + 1)
            .ToListAsync(cancellationToken);

        var hasMore = dbItems.Count > pageSize;
        var assets = hasMore ? dbItems.Take(pageSize).ToList() : dbItems;

        var items = assets.Select(asset => new TimelineResponse
        {
            Id = asset.Id,
            FileName = asset.FileName,
            FullPath = asset.FullPath,
            FileSize = asset.FileSize,
            CreatedDate = asset.CreatedDate,
            ModifiedDate = asset.ModifiedDate,
            Extension = asset.Extension,
            ScannedAt = asset.ScannedAt,
            Type = asset.Type.ToString(),
            Checksum = asset.Checksum,
            HasExif = asset.Exif != null,
            HasThumbnails = asset.Thumbnails.Any(),
            SyncStatus = AssetSyncStatus.Synced,
            Width = asset.Exif?.Width,
            Height = asset.Exif?.Height,
            IsFavorite = true,
            Tags = BuildTagList(asset)
        }).ToList();

        var nextCursor = hasMore ? assets.Last().CreatedDate : (DateTime?)null;

        return Results.Ok(new
        {
            Items = items,
            HasMore = hasMore,
            NextCursor = nextCursor
        });
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
