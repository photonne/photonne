using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Folders;

/// <summary>A single (year, count) bucket, used by both the move preview and the
/// post-move summary to describe how assets split across Year subfolders.</summary>
public sealed record YearCount(int Year, int Count);

/// <summary>A year bucket with the actual asset ids that fall in it (newest year
/// first; ids within a year ordered by capture date desc). Powers the "Revisar"
/// grid that shows every thumbnail to be moved, grouped by year.</summary>
public sealed record YearGroup(int Year, IReadOnlyList<Guid> AssetIds);

/// <summary>Outcome of a move: how many files actually moved, plus the real
/// distribution of those assets across capture years (newest first). The
/// year split is what the client shows as the post-move summary.</summary>
public sealed record MoveResult(int Moved, IReadOnlyList<YearCount> YearBreakdown);

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
    /// <returns>The moved count plus the real per-year distribution of the assets
    /// that actually moved (missing source files are skipped and excluded from
    /// both).</returns>
    public static async Task<MoveResult> MoveAsync(
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
        var perYear = new Dictionary<int, int>();
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
            // The per-year split only describes a Year-organized move; a flat move
            // reports no breakdown (so the client shows no "repartidas en años").
            if (organizeByCaptureYear)
            {
                var year = asset.CapturedAt.Year;
                perYear[year] = perYear.GetValueOrDefault(year) + 1;
            }
        }

        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"folders:list:{userId}");
        cache.Remove($"folders:tree:{userId}");

        var breakdown = perYear
            .OrderByDescending(kv => kv.Key)
            .Select(kv => new YearCount(kv.Key, kv.Value))
            .ToList();
        return new MoveResult(moved, breakdown);
    }
}
