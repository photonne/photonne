using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Folders;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Assets;

/// <summary>
/// The shared-folder trash: assets users deleted from <c>/assets/shared/...</c>
/// land under <c>/assets/shared/_trash</c> (see <see cref="AssetsEndpoint"/>) and
/// are administered here. Visibility is per-item: an Admin sees every shared
/// deletion; a normal user sees only the items they deleted or the ones deleted
/// from a folder they manage (<c>CanManagePermissions</c>). Restore and purge
/// reuse the same soft-delete plumbing as the personal trash.
/// </summary>
public class SharedTrashEndpoint : IEndpoint
{
    // A shared-folder deletion is uniquely identified by a non-null
    // DeletedByUserId — that column is stamped only by the shared-space branch of
    // AssetsEndpoint.DeleteAssets (personal deletions leave it null). This is more
    // robust than a path prefix: the soft-delete flow only rewrites FullPath to
    // /assets/shared/_trash when the physical file actually exists on disk.
    private static IQueryable<Asset> SharedTrashQuery(ApplicationDbContext db)
        => db.Assets.Where(a => a.DeletedAt != null && a.DeletedByUserId != null);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/assets/shared-trash")
            .WithTags("Assets")
            .RequireAuthorization();

        group.MapGet("", ListSharedTrash)
            .WithName("GetSharedTrash")
            .WithDescription("Lists assets deleted from shared folders that the caller may administer");

        group.MapPost("restore", RestoreSharedTrash)
            .WithName("RestoreSharedTrash")
            .WithDescription("Restores selected shared-folder deletions to their original location");

        group.MapPost("purge", PurgeSharedTrash)
            .WithName("PurgeSharedTrash")
            .WithDescription("Permanently deletes selected shared-folder deletions");
    }

    private static async Task<IResult> ListSharedTrash(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int? pageSize,
        CancellationToken ct)
    {
        var size = pageSize is > 0 ? pageSize.Value : 150;
        if (size > 500) size = 500;

        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();
        var isAdmin = user.IsInRole("Admin");

        IQueryable<Asset> baseQuery = SharedTrashQuery(dbContext)
            .Include(a => a.Thumbnails)
            .Include(a => a.Exif)
            .Include(a => a.DeletedBy);

        // Non-admins only see their own deletions plus those from folders they
        // manage. Resolve the managed set among the distinct source folders
        // present so the visibility filter can run in SQL.
        if (!isAdmin)
        {
            var folderIds = await baseQuery
                .Where(a => a.DeletedFromFolderId != null)
                .Select(a => a.DeletedFromFolderId!.Value)
                .Distinct()
                .ToListAsync(ct);

            var managed = new List<Guid>();
            foreach (var fid in folderIds)
            {
                if (await FoldersEndpoint.CanManagePermissionsFolderAsync(dbContext, userId, fid, isAdmin, ct))
                {
                    managed.Add(fid);
                }
            }

            baseQuery = baseQuery.Where(a =>
                a.DeletedByUserId == userId ||
                (a.DeletedFromFolderId != null && managed.Contains(a.DeletedFromFolderId.Value)));
        }

        var query = baseQuery;
        if (cursor.HasValue)
        {
            var cursorUtc = cursor.Value.ToUniversalTime();
            query = query.Where(a => a.DeletedAt < cursorUtc);
        }

        var dbItems = await query
            .OrderByDescending(a => a.DeletedAt)
            .ThenBy(a => a.Id)
            .Take(size + 1)
            .ToListAsync(ct);

        var hasMore = dbItems.Count > size;
        var assets = hasMore ? dbItems.Take(size).ToList() : dbItems;

        var items = assets.Select(ToItem).ToList();
        var nextCursor = hasMore ? assets.Last().DeletedAt : (DateTime?)null;

        return Results.Ok(new
        {
            Items = items,
            HasMore = hasMore,
            NextCursor = nextCursor
        });
    }

    private static async Task<IResult> RestoreSharedTrash(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromBody] RestoreAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var (assets, error) = await LoadAuthorizedAsync(dbContext, request.AssetIds, user, ct);
        if (error != null) return error;

        var username = user.GetUsername() ?? string.Empty;
        await AssetsEndpoint.RestoreAssetsInternalAsync(dbContext, settingsService, assets!, username, ct);
        return Results.NoContent();
    }

    private static async Task<IResult> PurgeSharedTrash(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromBody] PurgeAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var (assets, error) = await LoadAuthorizedAsync(dbContext, request.AssetIds, user, ct, includeThumbnails: true);
        if (error != null) return error;

        await AssetsEndpoint.DeleteAssetsPermanentlyAsync(dbContext, settingsService, assets!, ct);
        return Results.NoContent();
    }

    // Loads the requested shared-trash assets and verifies the caller may act on
    // every one of them. Returns a Forbid/BadRequest/NotFound result on failure.
    private static async Task<(List<Asset>? assets, IResult? error)> LoadAuthorizedAsync(
        ApplicationDbContext dbContext,
        List<Guid> assetIds,
        ClaimsPrincipal user,
        CancellationToken ct,
        bool includeThumbnails = false)
    {
        if (!TryGetUserId(user, out var userId)) return (null, Results.Unauthorized());
        if (assetIds == null || assetIds.Count == 0)
            return (null, Results.BadRequest(new { error = "Debes seleccionar al menos un asset." }));

        var isAdmin = user.IsInRole("Admin");

        IQueryable<Asset> q = SharedTrashQuery(dbContext);
        if (includeThumbnails) q = q.Include(a => a.Thumbnails);
        var assets = await q
            .Where(a => assetIds.Contains(a.Id))
            .ToListAsync(ct);

        if (assets.Count == 0)
            return (null, Results.NotFound(new { error = "Assets no encontrados." }));

        foreach (var asset in assets)
        {
            if (!await CanActOnSharedTrashAsync(dbContext, asset, userId, isAdmin, ct))
            {
                return (null, Results.Forbid());
            }
        }

        return (assets, null);
    }

    // Admin, the user who deleted it, or a manager of the original folder.
    private static async Task<bool> CanActOnSharedTrashAsync(
        ApplicationDbContext dbContext, Asset asset, Guid userId, bool isAdmin, CancellationToken ct)
    {
        if (isAdmin) return true;
        if (asset.DeletedByUserId == userId) return true;
        if (asset.DeletedFromFolderId is Guid fid)
        {
            return await FoldersEndpoint.CanManagePermissionsFolderAsync(dbContext, userId, fid, isAdmin, ct);
        }
        return false;
    }

    private static SharedTrashItemResponse ToItem(Asset asset)
    {
        string? folderName = null;
        if (!string.IsNullOrEmpty(asset.DeletedFromPath))
        {
            var dir = Path.GetDirectoryName(asset.DeletedFromPath.Replace('\\', '/'));
            if (!string.IsNullOrEmpty(dir)) folderName = Path.GetFileName(dir.TrimEnd('/'));
        }

        return new SharedTrashItemResponse
        {
            Id = asset.Id,
            FileName = asset.FileName,
            FullPath = asset.FullPath,
            FileSize = asset.FileSize,
            Type = asset.Type.ToString(),
            Extension = asset.Extension,
            HasThumbnails = asset.Thumbnails.Any(),
            Width = asset.Exif?.Width,
            Height = asset.Exif?.Height,
            DeletedAt = asset.DeletedAt,
            DeletedByUsername = asset.DeletedBy?.Username,
            DeletedFromPath = asset.DeletedFromPath,
            DeletedFromFolderName = folderName
        };
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        userId = Guid.Empty;
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return claim != null && Guid.TryParse(claim.Value, out userId);
    }
}

public class SharedTrashItemResponse
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public string Type { get; set; } = string.Empty;
    public string Extension { get; set; } = string.Empty;
    public bool HasThumbnails { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public DateTime? DeletedAt { get; set; }
    public string? DeletedByUsername { get; set; }
    public string? DeletedFromPath { get; set; }
    public string? DeletedFromFolderName { get; set; }
}
