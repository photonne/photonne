using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Assets;

public class AssetsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/assets")
            .WithTags("Assets")
            .RequireAuthorization();

        group.MapPost("delete", DeleteAssets)
            .WithName("DeleteAssets")
            .WithDescription("Moves assets to the user's trash and removes them from the library");

        group.MapPost("restore", RestoreAssets)
            .WithName("RestoreAssets")
            .WithDescription("Restores assets from the user's _trash");

        group.MapPost("purge", PurgeAssets)
            .WithName("PurgeAssets")
            .WithDescription("Permanently deletes assets from the user's trash");

        group.MapPost("trash/restore-all", RestoreAllTrash)
            .WithName("RestoreAllTrash")
            .WithDescription("Restores all assets from the user's trash");

        group.MapPost("trash/empty", EmptyTrash)
            .WithName("EmptyTrash")
            .WithDescription("Permanently deletes all assets from the user's trash");
    }

    private static async Task<IResult> DeleteAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromBody] DeleteAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        if (request.AssetIds == null || request.AssetIds.Count == 0)
        {
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });
        }

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id))
            .ToListAsync(ct);

        if (assets.Count == 0)
        {
            return Results.NotFound(new { error = "Assets no encontrados." });
        }

        if (!isAdmin && assets.Any(a => !IsAssetInUserRoot(a.FullPath, userId)))
        {
            return Results.Forbid();
        }

        var trashVirtualRoot = $"/assets/users/{userId}/_trash";
        var dateFolder = DateTime.UtcNow.ToString("yyyy-MM-dd");
        var dateVirtualPath = $"{trashVirtualRoot}/{dateFolder}";
        var datePhysicalPath = await settingsService.ResolvePhysicalPathAsync(dateVirtualPath);
        Directory.CreateDirectory(datePhysicalPath);

        var binFolder = await EnsureFolderRecordAsync(dbContext, userId, trashVirtualRoot, ct);
        var dateFolderRecord = await EnsureFolderRecordAsync(dbContext, userId, dateVirtualPath, ct, binFolder?.Id);

        foreach (var asset in assets)
        {
            if (asset.DeletedAt != null)
            {
                continue;
            }

            var originalPath = asset.FullPath;
            var originalFolderId = asset.FolderId;
            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (File.Exists(physicalPath))
            {
                var timestamp = DateTime.UtcNow.ToString("yyyyMMdd_HHmmss");
                var fileName = $"{timestamp}_{Path.GetFileName(physicalPath)}";
                var targetPath = Path.Combine(datePhysicalPath, fileName);

                if (File.Exists(targetPath))
                {
                    var uniqueName = $"{timestamp}_{Guid.NewGuid():N}_{Path.GetFileName(physicalPath)}";
                    targetPath = Path.Combine(datePhysicalPath, uniqueName);
                }

                File.Move(physicalPath, targetPath);
                asset.FileName = Path.GetFileName(targetPath);
                asset.FullPath = await settingsService.VirtualizePathAsync(targetPath);
            }

            asset.DeletedAt = DateTime.UtcNow;
            asset.DeletedFromPath = asset.DeletedFromPath ?? originalPath;
            asset.DeletedFromFolderId = originalFolderId;
            asset.FolderId = dateFolderRecord?.Id;
        }

        var albumAssets = await dbContext.AlbumAssets
            .Where(a => request.AssetIds.Contains(a.AssetId))
            .ToListAsync(ct);
        dbContext.AlbumAssets.RemoveRange(albumAssets);

        await dbContext.SaveChangesAsync(ct);

        return Results.NoContent();
    }

    private static async Task<IResult> RestoreAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromBody] RestoreAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        if (request.AssetIds == null || request.AssetIds.Count == 0)
        {
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });
        }

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id))
            .ToListAsync(ct);

        if (!isAdmin && assets.Any(a => a.DeletedAt == null || !IsAssetInUserRoot(a.FullPath, userId)))
        {
            return Results.Forbid();
        }

        var userRootVirtual = $"/assets/users/{userId}";

        await RestoreAssetsInternalAsync(dbContext, settingsService, assets, userId, ct);

        return Results.NoContent();
    }

    private static async Task<IResult> RestoreAllTrash(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Where(a => a.DeletedAt != null)
            .ToListAsync(ct);

        if (!isAdmin)
        {
            assets = assets.Where(a => IsAssetInUserRoot(a.FullPath, userId)).ToList();
        }

        if (!assets.Any())
        {
            return Results.NoContent();
        }

        await RestoreAssetsInternalAsync(dbContext, settingsService, assets, userId, ct);

        return Results.NoContent();
    }

    private static async Task RestoreAssetsInternalAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        List<Asset> assets,
        Guid userId,
        CancellationToken ct)
    {
        var userRootVirtual = $"/assets/users/{userId}";

        foreach (var asset in assets)
        {
            if (asset.DeletedAt == null)
            {
                continue;
            }

            var sourcePhysical = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            var targetVirtual = asset.DeletedFromPath ?? $"{userRootVirtual}/{asset.FileName}";
            var targetPhysical = await settingsService.ResolvePhysicalPathAsync(targetVirtual);
            var targetDirectory = Path.GetDirectoryName(targetPhysical);
            if (!string.IsNullOrEmpty(targetDirectory))
            {
                Directory.CreateDirectory(targetDirectory);
            }

            if (File.Exists(targetPhysical))
            {
                var uniqueName = $"{Path.GetFileNameWithoutExtension(targetPhysical)}_{Guid.NewGuid():N}{Path.GetExtension(targetPhysical)}";
                targetPhysical = Path.Combine(targetDirectory!, uniqueName);
                targetVirtual = await settingsService.VirtualizePathAsync(targetPhysical);
            }

            if (File.Exists(sourcePhysical))
            {
                File.Move(sourcePhysical, targetPhysical);
            }

            asset.FullPath = targetVirtual;
            asset.FileName = Path.GetFileName(targetPhysical);
            asset.FolderId = asset.DeletedFromFolderId;
            asset.DeletedAt = null;
            asset.DeletedFromPath = null;
            asset.DeletedFromFolderId = null;
        }

        await dbContext.SaveChangesAsync(ct);
    }

    private static async Task<IResult> PurgeAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromBody] PurgeAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        if (request.AssetIds == null || request.AssetIds.Count == 0)
        {
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });
        }

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Include(a => a.Thumbnails)
            .Where(a => request.AssetIds.Contains(a.Id) && a.DeletedAt != null)
            .ToListAsync(ct);

        if (!assets.Any())
        {
            return Results.NotFound(new { error = "Assets no encontrados." });
        }

        if (!isAdmin && assets.Any(a => !IsAssetInUserRoot(a.FullPath, userId)))
        {
            return Results.Forbid();
        }

        await DeleteAssetsPermanentlyAsync(dbContext, settingsService, assets, ct);

        return Results.NoContent();
    }

    private static async Task<IResult> EmptyTrash(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Include(a => a.Thumbnails)
            .Where(a => a.DeletedAt != null)
            .ToListAsync(ct);

        if (!isAdmin)
        {
            assets = assets.Where(a => IsAssetInUserRoot(a.FullPath, userId)).ToList();
        }

        if (!assets.Any())
        {
            return Results.NoContent();
        }

        await DeleteAssetsPermanentlyAsync(dbContext, settingsService, assets, ct);

        return Results.NoContent();
    }

    private static async Task DeleteAssetsPermanentlyAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        List<Asset> assets,
        CancellationToken ct)
    {
        foreach (var asset in assets)
        {
            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (File.Exists(physicalPath))
            {
                File.Delete(physicalPath);
            }

            foreach (var thumbnail in asset.Thumbnails)
            {
                if (!string.IsNullOrEmpty(thumbnail.FilePath) && File.Exists(thumbnail.FilePath))
                {
                    File.Delete(thumbnail.FilePath);
                }
            }
        }

        var assetIds = assets.Select(a => a.Id).ToList();
        var albumAssets = await dbContext.AlbumAssets
            .Where(aa => assetIds.Contains(aa.AssetId))
            .ToListAsync(ct);

        dbContext.AlbumAssets.RemoveRange(albumAssets);
        dbContext.Assets.RemoveRange(assets);
        await dbContext.SaveChangesAsync(ct);
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        userId = Guid.Empty;
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return userIdClaim != null && Guid.TryParse(userIdClaim.Value, out userId);
    }

    private static bool IsAssetInUserRoot(string assetPath, Guid userId)
    {
        var normalized = assetPath.Replace('\\', '/');
        var virtualRoot = $"/assets/users/{userId}/";
        if (normalized.StartsWith(virtualRoot, StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return normalized.Contains($"/users/{userId}/", StringComparison.OrdinalIgnoreCase);
    }

    private static async Task<Folder?> EnsureFolderRecordAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string folderPath,
        CancellationToken ct,
        Guid? parentFolderId = null)
    {
        var normalizedPath = folderPath.Replace('\\', '/').TrimEnd('/');
        var existing = await dbContext.Folders.FirstOrDefaultAsync(f => f.Path == normalizedPath, ct);
        if (existing != null)
        {
            return existing;
        }

        var folder = new Folder
        {
            Path = normalizedPath,
            Name = Path.GetFileName(normalizedPath),
            ParentFolderId = parentFolderId
        };

        dbContext.Folders.Add(folder);
        await dbContext.SaveChangesAsync(ct);

        var hasPermission = await dbContext.FolderPermissions
            .AnyAsync(p => p.UserId == userId && p.FolderId == folder.Id, ct);
        if (!hasPermission)
        {
            dbContext.FolderPermissions.Add(new FolderPermission
            {
                UserId = userId,
                FolderId = folder.Id,
                CanRead = true,
                CanWrite = true,
                CanDelete = true,
                CanManagePermissions = true,
                GrantedByUserId = userId
            });
            await dbContext.SaveChangesAsync(ct);
        }

        return folder;
    }
}

public class DeleteAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class RestoreAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class PurgeAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}
