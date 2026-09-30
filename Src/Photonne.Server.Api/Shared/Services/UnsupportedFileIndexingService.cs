using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Mirrors <see cref="AssetIndexingService.IndexFileAsync"/> but for files whose
/// extension isn't a recognised image/video. Stores a row in the parallel
/// <see cref="UnsupportedFile"/> catalogue (no checksum, no enrichment) so the
/// user can see/download everything that physically exists in their storage.
/// </summary>
public class UnsupportedFileIndexingService
{
    private readonly SettingsService _settingsService;
    private readonly AssetIndexingService _assetIndexingService;
    private readonly ApplicationDbContext _dbContext;

    public UnsupportedFileIndexingService(
        SettingsService settingsService,
        AssetIndexingService assetIndexingService,
        ApplicationDbContext dbContext)
    {
        _settingsService = settingsService;
        _assetIndexingService = assetIndexingService;
        _dbContext = dbContext;
    }

    public async Task<UnsupportedFile?> IndexUnsupportedFileAsync(
        ScannedFile file,
        Guid userId,
        CancellationToken ct,
        Guid? externalLibraryId = null)
    {
        var physicalPath = file.FullPath;
        if (!File.Exists(physicalPath))
            return null;

        try
        {
            var storedPath = await ToStoredPathAsync(physicalPath, externalLibraryId);

            var existing = await _dbContext.UnsupportedFiles
                .FirstOrDefaultAsync(u => u.FullPath == storedPath, ct);

            var (ownerId, folderId) = await _assetIndexingService.ResolveOwnerAndFolderAsync(
                physicalPath, storedPath, userId, externalLibraryId, ct);

            if (existing != null)
            {
                // Keep metadata fresh on re-scan.
                existing.FileName = file.FileName;
                existing.FileSize = file.FileSize;
                existing.Extension = file.Extension;
                existing.FileCreatedAt = file.FileCreatedAt;
                existing.FileModifiedAt = file.FileModifiedAt;
                existing.OwnerId = ownerId;
                existing.FolderId = folderId;
                existing.ExternalLibraryId = externalLibraryId;
                await _dbContext.SaveChangesAsync(ct);
                return existing;
            }

            var entity = new UnsupportedFile
            {
                FileName = file.FileName,
                FullPath = storedPath,
                FileSize = file.FileSize,
                Extension = file.Extension,
                FileCreatedAt = file.FileCreatedAt,
                FileModifiedAt = file.FileModifiedAt,
                DiscoveredAt = DateTime.UtcNow,
                OwnerId = ownerId,
                FolderId = folderId,
                ExternalLibraryId = externalLibraryId
            };

            _dbContext.UnsupportedFiles.Add(entity);
            await _dbContext.SaveChangesAsync(ct);
            return entity;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[UNSUPPORTED-INDEX] Error cataloguing {physicalPath}: {ex.Message}");
            return null;
        }
    }

    /// <summary>
    /// Drops the catalogue rows of one scan scope (the internal storage, or one
    /// external library) that the scan no longer reports as unsupported: the
    /// file was deleted, renamed or moved, or its type is now recognised media
    /// and it has become an Asset. Without this a row outlives its file forever.
    /// Call it only after a scan that completed — <paramref name="stillUnsupported"/>
    /// must be everything the scope holds.
    /// </summary>
    public async Task<int> PruneStaleAsync(
        IReadOnlyCollection<ScannedFile> stillUnsupported,
        Guid? externalLibraryId,
        CancellationToken ct)
    {
        // Ordinal: IndexUnsupportedFileAsync matches rows by exact path.
        var keep = new HashSet<string>(StringComparer.Ordinal);
        foreach (var file in stillUnsupported)
            keep.Add(await ToStoredPathAsync(file.FullPath, externalLibraryId));

        var rows = await _dbContext.UnsupportedFiles
            .AsNoTracking()
            .Where(u => u.ExternalLibraryId == externalLibraryId)
            .Select(u => new { u.Id, u.FullPath })
            .ToListAsync(ct);

        var staleIds = rows.Where(r => !keep.Contains(r.FullPath)).Select(r => r.Id).ToList();
        foreach (var chunk in staleIds.Chunk(1000))
        {
            await _dbContext.UnsupportedFiles
                .Where(u => chunk.Contains(u.Id))
                .ExecuteDeleteAsync(ct);
        }

        if (staleIds.Count > 0)
            Console.WriteLine($"[UNSUPPORTED-INDEX] Purged {staleIds.Count} stale catalogue rows");
        return staleIds.Count;
    }

    // External-library files store the physical path directly; internal files
    // use a normalized virtual path — same rule as Asset.
    private async Task<string> ToStoredPathAsync(string physicalPath, Guid? externalLibraryId) =>
        externalLibraryId.HasValue
            ? NormalizeVirtualPath(physicalPath)
            : NormalizeVirtualPath(await _settingsService.VirtualizePathAsync(physicalPath));

    private static string NormalizeVirtualPath(string path) =>
        path.Replace('\\', '/').TrimEnd('/');
}
