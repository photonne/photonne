using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Folders;
using Photonne.Server.Api.Shared.Authorization;
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
        [FromServices] INotificationService notifications,
        [FromBody] DeleteAssetsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
        {
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });
        }

        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id))
            .ToListAsync(ct);

        if (assets.Count == 0)
        {
            return Results.NotFound(new { error = "Assets no encontrados." });
        }

        var authorized = await TrashOrDeleteAssetsAsync(
            dbContext, settingsService, notifications, assets, userId, username, user.IsInRole("Admin"), ct);

        return authorized ? Results.NoContent() : Results.Forbid();
    }

    // Core delete flow shared by DeleteAssets and folder deletion. Partitions the
    // given (live) assets by space: personal-space assets go to the user's personal
    // trash; shared-space assets go to the communal admin trash, gated by CanDelete
    // on their folder (same rule as deleting the folder). If the trash is globally
    // disabled, deletes permanently instead. Cleans up album links, saves, and
    // notifies shared-folder managers. Returns false (no side effects committed) if
    // any asset fails authorization.
    internal static async Task<bool> TrashOrDeleteAssetsAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        INotificationService notifications,
        List<Asset> assets,
        Guid userId,
        string username,
        bool isAdmin,
        CancellationToken ct)
    {
        if (assets.Count == 0)
        {
            return true;
        }

        // Partition by space. Personal-space assets follow the existing flow
        // (personal trash). Shared-space assets go to a communal admin trash,
        // gated by CanDelete on their folder (same rule as deleting the folder).
        var personalAssets = new List<Asset>();
        var sharedAssets = new List<Asset>();
        foreach (var asset in assets)
        {
            if (IsAssetInUserRoot(asset.FullPath, username))
            {
                personalAssets.Add(asset);
            }
            else if (FoldersEndpoint.IsInSharedSpace(asset.FullPath))
            {
                if (asset.FolderId is not Guid folderId ||
                    !await FoldersEndpoint.CanDeleteFolderAsync(dbContext, userId, folderId, isAdmin, ct))
                {
                    return false;
                }
                sharedAssets.Add(asset);
            }
            else
            {
                return false;
            }
        }

        var assetIds = assets.Select(a => a.Id).ToList();

        // If trash is disabled, delete permanently instead of moving to _trash
        var trashEnabled = await settingsService.GetSettingAsync("TrashSettings.Enabled", Guid.Empty, "true");
        if (!trashEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
        {
            var withThumbnails = await dbContext.Assets
                .Include(a => a.Thumbnails)
                .Where(a => assetIds.Contains(a.Id))
                .ToListAsync(ct);
            await DeleteAssetsPermanentlyAsync(dbContext, settingsService, withThumbnails, ct);
            return true;
        }

        if (personalAssets.Count > 0)
        {
            await TrashAssetsAsync(dbContext, settingsService, personalAssets,
                $"/assets/users/{username}/_trash", userId, deletedByUserId: null, grantPermission: true, ct);
        }
        if (sharedAssets.Count > 0)
        {
            await TrashAssetsAsync(dbContext, settingsService, sharedAssets,
                "/assets/shared/_trash", userId, deletedByUserId: userId, grantPermission: false, ct);
        }

        var albumAssets = await dbContext.AlbumAssets
            .Where(a => assetIds.Contains(a.AssetId))
            .ToListAsync(ct);
        dbContext.AlbumAssets.RemoveRange(albumAssets);

        await dbContext.SaveChangesAsync(ct);

        if (sharedAssets.Count > 0)
        {
            await NotifySharedDeletionAsync(dbContext, notifications, sharedAssets, userId, username, ct);
        }

        return true;
    }

    // Best-effort: notify each shared folder's managers plus all active admins
    // (minus the deleter) that assets were moved to the shared trash. Grouped per
    // folder so recipients get one aggregated notice, not one per asset. A
    // notification failure must never fail the delete.
    private static async Task NotifySharedDeletionAsync(
        ApplicationDbContext dbContext,
        INotificationService notifications,
        List<Asset> sharedAssets,
        Guid deleterUserId,
        string deleterUsername,
        CancellationToken ct)
    {
        try
        {
            var adminIds = await dbContext.Users
                .Where(u => u.Role == "Admin" && u.IsActive)
                .Select(u => u.Id)
                .ToListAsync(ct);

            var groups = sharedAssets
                .Where(a => a.DeletedFromFolderId.HasValue)
                .GroupBy(a => a.DeletedFromFolderId!.Value);

            foreach (var group in groups)
            {
                var folderId = group.Key;
                var count = group.Count();

                var managerIds = await FoldersEndpoint.GetFolderManagerUserIdsAsync(dbContext, folderId, ct);
                var recipients = managerIds.Concat(adminIds)
                    .Where(id => id != deleterUserId)
                    .Distinct()
                    .ToList();
                if (recipients.Count == 0) continue;

                var folderName = await dbContext.Folders
                    .AsNoTracking()
                    .Where(f => f.Id == folderId)
                    .Select(f => f.Name)
                    .FirstOrDefaultAsync(ct);
                if (string.IsNullOrEmpty(folderName)) folderName = "una carpeta compartida";

                var fileWord = count == 1 ? "archivo" : "archivos";
                var message = $"{deleterUsername} movió {count} {fileWord} de «{folderName}» a la papelera compartida.";

                foreach (var recipientId in recipients)
                {
                    await notifications.CreateAsync(
                        recipientId,
                        NotificationType.SharedAssetsDeleted,
                        "Archivos eliminados de una carpeta compartida",
                        message,
                        actionUrl: "/shared-trash");
                }
            }
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            Console.WriteLine($"[SHARED-TRASH] Failed to dispatch deletion notifications: {ex.Message}");
        }
    }

    // Moves a batch of assets into a trash root (personal or shared), moving the
    // physical file under {trashVirtualRoot}/{yyyy-MM-dd} and stamping the
    // soft-delete columns. deletedByUserId attributes shared deletions (null for
    // personal). grantPermission=false skips the folder auto-grant so the shared
    // trash root never becomes a manageable folder for the deleter.
    private static async Task TrashAssetsAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        List<Asset> assets,
        string trashVirtualRoot,
        Guid actingUserId,
        Guid? deletedByUserId,
        bool grantPermission,
        CancellationToken ct)
    {
        var dateFolder = DateTime.UtcNow.ToString("yyyy-MM-dd");
        var dateVirtualPath = $"{trashVirtualRoot}/{dateFolder}";
        var datePhysicalPath = await settingsService.ResolvePhysicalPathAsync(dateVirtualPath);
        Directory.CreateDirectory(datePhysicalPath);

        var binFolder = await EnsureFolderRecordAsync(dbContext, actingUserId, trashVirtualRoot, ct, grantPermission: grantPermission);
        var dateFolderRecord = await EnsureFolderRecordAsync(dbContext, actingUserId, dateVirtualPath, ct, binFolder?.Id, grantPermission);

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
            asset.DeletedByUserId = deletedByUserId;
            asset.FolderId = dateFolderRecord?.Id;
        }
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
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
        {
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });
        }

        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id))
            .ToListAsync(ct);

        if (assets.Any(a => a.DeletedAt == null || !IsAssetInUserRoot(a.FullPath, username)))
        {
            return Results.Forbid();
        }

        await RestoreAssetsInternalAsync(dbContext, settingsService, assets, username, ct);

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
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var assets = await dbContext.Assets
            .Where(a => a.DeletedAt != null)
            .ToListAsync(ct);

        assets = assets.Where(a => IsAssetInUserRoot(a.FullPath, username)).ToList();

        if (!assets.Any())
        {
            return Results.NoContent();
        }

        await RestoreAssetsInternalAsync(dbContext, settingsService, assets, username, ct);

        return Results.NoContent();
    }

    // internal: reused by SharedTrashEndpoint's restore.
    internal static async Task RestoreAssetsInternalAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        List<Asset> assets,
        string username,
        CancellationToken ct)
    {
        var userRootVirtual = $"/assets/users/{username}";

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
            asset.DeletedByUserId = null;
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
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
        {
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });
        }

        var assets = await dbContext.Assets
            .Include(a => a.Thumbnails)
            .Where(a => request.AssetIds.Contains(a.Id) && a.DeletedAt != null)
            .ToListAsync(ct);

        if (!assets.Any())
        {
            return Results.NotFound(new { error = "Assets no encontrados." });
        }

        if (assets.Any(a => !IsAssetInUserRoot(a.FullPath, username)))
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
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var assets = await dbContext.Assets
            .Include(a => a.Thumbnails)
            .Where(a => a.DeletedAt != null)
            .ToListAsync(ct);

        assets = assets.Where(a => IsAssetInUserRoot(a.FullPath, username)).ToList();

        if (!assets.Any())
        {
            return Results.NoContent();
        }

        await DeleteAssetsPermanentlyAsync(dbContext, settingsService, assets, ct);

        return Results.NoContent();
    }

    // internal: reused by SharedTrashEndpoint's purge.
    internal static async Task DeleteAssetsPermanentlyAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        List<Asset> assets,
        CancellationToken ct)
    {
        foreach (var asset in assets)
        {
            // No borrar archivos físicos de bibliotecas externas — no son propiedad de Photonne
            if (!asset.ExternalLibraryId.HasValue)
            {
                var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (File.Exists(physicalPath))
                {
                    File.Delete(physicalPath);
                }
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

    private static bool IsAssetInUserRoot(string assetPath, string username)
    {
        var normalized = assetPath.Replace('\\', '/');
        var virtualRoot = $"/assets/users/{username}/";
        if (normalized.StartsWith(virtualRoot, StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return normalized.Contains($"/users/{username}/", StringComparison.OrdinalIgnoreCase);
    }

    private static async Task<Folder?> EnsureFolderRecordAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string folderPath,
        CancellationToken ct,
        Guid? parentFolderId = null,
        bool grantPermission = true)
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

        // The shared trash root must never grant the deleter permissions, or the
        // full-access auto-grant below would let any deleter manage the whole
        // shared trash (and thus see/restore everyone's deletions via inheritance).
        if (!grantPermission)
        {
            return folder;
        }

        // Inherited semantics: personal-space folders and folders already
        // covered by an ancestor grant don't need their own row.
        var hasPermission = await dbContext.FolderPermissions
            .AnyAsync(p => p.UserId == userId && p.FolderId == folder.Id, ct);
        if (!hasPermission &&
            !await FolderPermissionGuard.IsRedundantFullGrantAsync(dbContext, userId, folder.Id, ct))
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
