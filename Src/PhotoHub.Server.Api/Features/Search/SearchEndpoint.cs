using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Client.Shared.Models;
using PhotoHub.Server.Api.Features.Timeline;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Models;

namespace PhotoHub.Server.Api.Features.Search;

public class SearchEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/search", Handle)
            .WithName("SearchAssets")
            .WithTags("Assets")
            .WithDescription("Search assets by filename, path, tags or date range")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromQuery] string? q,
        [FromQuery] DateTime? from,
        [FromQuery] DateTime? to,
        [FromQuery] string? folder,
        [FromQuery] int pageSize,
        CancellationToken ct)
    {
        if (pageSize <= 0) pageSize = 100;
        if (pageSize > 200) pageSize = 200;

        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        // Require at least one filter to avoid returning everything
        if (string.IsNullOrWhiteSpace(q) && from == null && to == null && string.IsNullOrWhiteSpace(folder))
            return Results.Ok(new SearchResponse());

        var isAdmin = user.IsInRole("Admin");

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && !a.IsArchived);

        if (!isAdmin)
        {
            var allowedFolderIds = await GetAllowedFolderIdsAsync(dbContext, userId, ct);
            query = query.Where(a => a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));
        }

        // Text search: filename, full path, user tag names
        if (!string.IsNullOrWhiteSpace(q))
        {
            var tagTypeFilter = Enum.TryParse<AssetTagType>(q, ignoreCase: true, out var matchedType)
                ? (AssetTagType?)matchedType
                : null;

            query = query.Where(a =>
                a.FileName.Contains(q) ||
                a.FullPath.Contains(q) ||
                a.UserTags.Any(ut => ut.UserTag.Name.Contains(q)) ||
                (tagTypeFilter.HasValue && a.Tags.Any(t => t.TagType == tagTypeFilter.Value)));
        }

        // Date range
        if (from.HasValue)
        {
            var fromUtc = from.Value.ToUniversalTime();
            query = query.Where(a => a.CreatedDate >= fromUtc);
        }

        if (to.HasValue)
        {
            // Include the full selected day
            var toUtc = to.Value.ToUniversalTime().Date.AddDays(1);
            query = query.Where(a => a.CreatedDate < toUtc);
        }

        // Folder path substring
        if (!string.IsNullOrWhiteSpace(folder))
            query = query.Where(a => a.FullPath.Contains(folder));

        var dbItems = await query
            .OrderByDescending(a => a.CreatedDate)
            .ThenByDescending(a => a.ModifiedDate)
            .Take(pageSize + 1)
            .ToListAsync(ct);

        var hasMore = dbItems.Count > pageSize;
        var assets = hasMore ? dbItems.Take(pageSize).ToList() : dbItems;

        var items = assets.Select(a => new TimelineResponse
        {
            Id = a.Id,
            FileName = a.FileName,
            FullPath = a.FullPath,
            FileSize = a.FileSize,
            CreatedDate = a.CreatedDate,
            ModifiedDate = a.ModifiedDate,
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
            IsFavorite = a.IsFavorite
        }).ToList();

        return Results.Ok(new SearchResponse { Items = items, HasMore = hasMore });
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

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
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
            .Select(p => p.FolderId)
            .Distinct()
            .ToHashSetAsync(ct);

        var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();
        foreach (var f in allFolders)
        {
            if (!foldersWithPermissions.Contains(f.Id) &&
                f.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
            {
                allowedIds.Add(f.Id);
            }
        }
        return allowedIds;
    }
}

public class SearchResponse
{
    public List<TimelineResponse> Items { get; set; } = new();
    public bool HasMore { get; set; }
}
