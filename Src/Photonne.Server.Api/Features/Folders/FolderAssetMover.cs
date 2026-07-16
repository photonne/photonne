using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Folders;

/// <summary>
/// Shared physical move of assets into a target folder: <c>File.Move</c>
/// (collision → <c>_{Guid:N}</c> suffix), update <c>FolderId</c>/<c>FileName</c>/
/// <c>FullPath</c>, persist, and evict the user's folder-list/tree caches.
/// Authorization is the caller's responsibility — this only moves files.
/// Reused by <see cref="FoldersEndpoint"/> (manual multi-select move) and the
/// Organize rule-move endpoint (condition-based move out of MobileBackup), so the
/// two paths can never drift on collision handling or cache eviction.
/// </summary>
internal static class FolderAssetMover
{
    /// <param name="assets">Tracked assets to move (must be tracked so the
    /// <c>FolderId</c>/path updates persist on <c>SaveChanges</c>).</param>
    /// <param name="organizeByCaptureYear">When true, each asset is filed into a
    /// Year subfolder (e.g. <c>2026</c>) resolved-or-created under
    /// <paramref name="targetFolder"/>, derived from its (naive-local)
    /// <c>CapturedAt</c>. Year buckets are reused across moves (idempotent).</param>
    /// <returns>Number of assets whose file was actually moved (missing source
    /// files are skipped).</returns>
    public static async Task<int> MoveAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        IMemoryCache cache,
        Guid userId,
        IReadOnlyList<Asset> assets,
        Folder targetFolder,
        CancellationToken cancellationToken,
        bool organizeByCaptureYear = false)
    {
        // Cache of destination folder → resolved physical path, so we call
        // Resolve-or-create + Directory.CreateDirectory once per bucket, not per asset.
        var bucketPaths = new Dictionary<Guid, string>();

        async Task<(Folder folder, string physicalPath)> ResolveBucketAsync(Asset asset)
        {
            var folder = targetFolder;
            if (organizeByCaptureYear)
            {
                folder = await FoldersEndpoint.EnsureChildFolderAsync(
                    dbContext, settingsService, cache, userId, targetFolder,
                    asset.CapturedAt.Year.ToString(), cancellationToken);
            }

            if (!bucketPaths.TryGetValue(folder.Id, out var physical))
            {
                physical = await settingsService.ResolvePhysicalPathAsync(folder.Path);
                Directory.CreateDirectory(physical);
                bucketPaths[folder.Id] = physical;
            }
            return (folder, physical);
        }

        var moved = 0;
        foreach (var asset in assets)
        {
            var sourcePhysicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (!File.Exists(sourcePhysicalPath))
            {
                continue;
            }

            var (bucketFolder, targetPhysicalPath) = await ResolveBucketAsync(asset);

            var fileName = Path.GetFileName(sourcePhysicalPath);
            var newPhysicalPath = Path.Combine(targetPhysicalPath, fileName);
            if (File.Exists(newPhysicalPath))
            {
                var uniqueName = $"{Path.GetFileNameWithoutExtension(fileName)}_{Guid.NewGuid():N}{Path.GetExtension(fileName)}";
                newPhysicalPath = Path.Combine(targetPhysicalPath, uniqueName);
                fileName = uniqueName;
            }

            File.Move(sourcePhysicalPath, newPhysicalPath);

            asset.FolderId = bucketFolder.Id;
            asset.FileName = fileName;
            asset.FullPath = await settingsService.VirtualizePathAsync(newPhysicalPath);
            moved++;
        }

        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"folders:list:{userId}");
        cache.Remove($"folders:tree:{userId}");

        return moved;
    }
}
