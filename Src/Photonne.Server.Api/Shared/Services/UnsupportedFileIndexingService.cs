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
            // External-library files store the physical path directly; internal
            // files use a normalized virtual path — same rule as Asset.
            var storedPath = externalLibraryId.HasValue
                ? NormalizeVirtualPath(physicalPath)
                : NormalizeVirtualPath(await _settingsService.VirtualizePathAsync(physicalPath));

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

    private static string NormalizeVirtualPath(string path) =>
        path.Replace('\\', '/').TrimEnd('/');
}
