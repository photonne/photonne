using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Archive;

public class ArchiveEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/assets")
            .WithTags("Assets")
            .RequireAuthorization();

        group.MapGet("archived", GetArchived)
            .WithName("GetArchived")
            .WithDescription("Gets all archived assets for the current user");

        group.MapPost("archive", ArchiveAssets)
            .WithName("ArchiveAssets")
            .WithDescription("Archives the specified assets");

        group.MapPost("unarchive", UnarchiveAssets)
            .WithName("UnarchiveAssets")
            .WithDescription("Unarchives the specified assets");

        group.MapPost("archive/unarchive-all", UnarchiveAll)
            .WithName("UnarchiveAll")
            .WithDescription("Unarchives all archived assets for the current user");
    }

    private static async Task<IResult> GetArchived(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int pageSize,
        CancellationToken ct)
    {
        if (pageSize <= 0) pageSize = 150;
        if (pageSize > 500) pageSize = 500;

        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        var isAdmin = user.IsInRole("Admin");
        var userRootPath = $"/assets/users/{userId}";

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && a.IsArchived);

        if (!isAdmin)
        {
            var allFolders = await dbContext.Folders.ToListAsync(ct);
            var permissions = await dbContext.FolderPermissions
                .Where(p => p.UserId == userId && p.CanRead)
                .ToListAsync(ct);

            var foldersWithPermissionsSet = await dbContext.FolderPermissions
                .Select(p => p.FolderId)
                .Distinct()
                .ToHashSetAsync(ct);

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
            .ToListAsync(ct);

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
            IsFavorite = asset.IsFavorite,
            IsArchived = true,
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

    private static async Task<IResult> ArchiveAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromBody] ArchiveAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id) && a.DeletedAt == null)
            .ToListAsync(ct);

        if (!isAdmin && assets.Any(a => !IsAssetInUserRoot(a.FullPath, userId)))
            return Results.Forbid();

        foreach (var asset in assets)
            asset.IsArchived = true;

        await dbContext.SaveChangesAsync(ct);
        return Results.NoContent();
    }

    private static async Task<IResult> UnarchiveAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromBody] UnarchiveAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id) && a.IsArchived)
            .ToListAsync(ct);

        if (!isAdmin && assets.Any(a => !IsAssetInUserRoot(a.FullPath, userId)))
            return Results.Forbid();

        foreach (var asset in assets)
            asset.IsArchived = false;

        await dbContext.SaveChangesAsync(ct);
        return Results.NoContent();
    }

    private static async Task<IResult> UnarchiveAll(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Where(a => a.IsArchived && a.DeletedAt == null)
            .ToListAsync(ct);

        if (!isAdmin)
            assets = assets.Where(a => IsAssetInUserRoot(a.FullPath, userId)).ToList();

        if (!assets.Any())
            return Results.NoContent();

        foreach (var asset in assets)
            asset.IsArchived = false;

        await dbContext.SaveChangesAsync(ct);
        return Results.NoContent();
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        userId = Guid.Empty;
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return claim != null && Guid.TryParse(claim.Value, out userId);
    }

    private static bool IsAssetInUserRoot(string assetPath, Guid userId)
    {
        var normalized = assetPath.Replace('\\', '/');
        return normalized.StartsWith($"/assets/users/{userId}/", StringComparison.OrdinalIgnoreCase) ||
               normalized.Contains($"/users/{userId}/", StringComparison.OrdinalIgnoreCase);
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

public class ArchiveAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class UnarchiveAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}
