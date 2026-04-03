using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class AssetIndexingService
{
    private readonly FileHashService _hashService;
    private readonly ExifExtractorService _exifService;
    private readonly ThumbnailGeneratorService _thumbnailService;
    private readonly MediaRecognitionService _mediaRecognitionService;
    private readonly IMlJobService _mlJobService;
    private readonly SettingsService _settingsService;
    private readonly ApplicationDbContext _dbContext;

    public AssetIndexingService(
        FileHashService hashService,
        ExifExtractorService exifService,
        ThumbnailGeneratorService thumbnailService,
        MediaRecognitionService mediaRecognitionService,
        IMlJobService mlJobService,
        SettingsService settingsService,
        ApplicationDbContext dbContext)
    {
        _hashService = hashService;
        _exifService = exifService;
        _thumbnailService = thumbnailService;
        _mediaRecognitionService = mediaRecognitionService;
        _mlJobService = mlJobService;
        _settingsService = settingsService;
        _dbContext = dbContext;
    }

    /// <summary>
    /// Indexes a single file into the database. When <paramref name="externalLibraryId"/> is set
    /// the file is treated as part of an external library: the physical path is stored as-is,
    /// the provided <paramref name="userId"/> is used as owner, and no checksum-based rename
    /// detection is performed (to avoid matching unrelated internal assets).
    /// </summary>
    public async Task<Asset?> IndexFileAsync(
        string physicalPath,
        Guid userId,
        CancellationToken ct,
        Guid? externalLibraryId = null)
    {
        if (!File.Exists(physicalPath))
        {
            Console.WriteLine($"[INDEX-FILE] File not found: {physicalPath}");
            return null;
        }

        try
        {
            // External library assets store the physical path directly;
            // internal assets use a normalized virtual path.
            var storedPath = externalLibraryId.HasValue
                ? NormalizeVirtualPath(physicalPath)
                : NormalizeVirtualPath(await _settingsService.VirtualizePathAsync(physicalPath));

            var checksum = await _hashService.CalculateFileHashAsync(physicalPath, ct);

            // Check if already indexed by path
            var existingByPath = await _dbContext.Assets
                .Include(a => a.Thumbnails)
                .Include(a => a.Exif)
                .FirstOrDefaultAsync(a => a.FullPath == storedPath && a.DeletedAt == null, ct);

            if (existingByPath != null && existingByPath.Thumbnails.Any())
            {
                Console.WriteLine($"[INDEX-FILE] Already indexed: {storedPath}");
                return existingByPath;
            }

            // Check by checksum (handles renames/moves) — only for internal assets to avoid
            // accidentally matching unrelated files from different sources.
            Asset? existingByChecksum = null;
            if (!externalLibraryId.HasValue)
            {
                existingByChecksum = await _dbContext.Assets
                    .Include(a => a.Thumbnails)
                    .Include(a => a.Exif)
                    .FirstOrDefaultAsync(a => a.Checksum == checksum && a.DeletedAt == null, ct);

                if (existingByChecksum != null && existingByChecksum.Thumbnails.Any())
                {
                    Console.WriteLine($"[INDEX-FILE] Already indexed by checksum: {storedPath}");
                    return existingByChecksum;
                }
            }

            var fileInfo = new FileInfo(physicalPath);
            var extension = fileInfo.Extension.TrimStart('.').ToLowerInvariant();
            var assetType = DetermineAssetType(extension);

            Asset asset;
            bool isNew;

            if (existingByPath != null)
            {
                // Update existing
                existingByPath.Checksum = checksum;
                existingByPath.FileSize = fileInfo.Length;
                existingByPath.ModifiedDate = fileInfo.LastWriteTimeUtc;
                existingByPath.IsOffline = false;
                asset = existingByPath;
                isNew = false;
            }
            else if (existingByChecksum != null)
            {
                // File moved/renamed — update path
                existingByChecksum.FullPath = storedPath;
                existingByChecksum.FileName = fileInfo.Name;
                existingByChecksum.ModifiedDate = fileInfo.LastWriteTimeUtc;
                asset = existingByChecksum;
                isNew = false;
            }
            else
            {
                asset = new Asset
                {
                    FileName = fileInfo.Name,
                    FullPath = storedPath,
                    FileSize = fileInfo.Length,
                    Checksum = checksum,
                    Type = assetType,
                    Extension = extension,
                    CreatedDate = fileInfo.CreationTimeUtc,
                    ModifiedDate = fileInfo.LastWriteTimeUtc,
                    ScannedAt = DateTime.UtcNow,
                };
                isNew = true;
            }

            // Owner & library assignment
            if (externalLibraryId.HasValue)
            {
                asset.OwnerId = userId;
                asset.ExternalLibraryId = externalLibraryId;
            }
            else
            {
                // Owner from path — validate that the extracted user actually exists in the DB.
                // If not, fall back to the primary admin to avoid FK constraint violations.
                if (TryGetOwnerIdFromVirtualPath(storedPath, out var parsedOwner))
                {
                    var ownerExists = await _dbContext.Users.AnyAsync(u => u.Id == parsedOwner, ct);
                    if (ownerExists)
                    {
                        asset.OwnerId = parsedOwner;
                    }
                    else
                    {
                        var primaryAdmin = await GetPrimaryAdminIdAsync(ct);
                        asset.OwnerId = primaryAdmin;
                        Console.WriteLine($"[INDEX-FILE] Owner {parsedOwner} not found, assigned to primary admin ({primaryAdmin}).");
                    }
                }
                else
                {
                    // Non-user path (e.g. /shared) — assign to primary admin
                    asset.OwnerId = await GetPrimaryAdminIdAsync(ct);
                }
            }

            // Folder
            var folderPath = Path.GetDirectoryName(physicalPath);
            var folder = await GetOrCreateFolderForPathAsync(folderPath, ct);
            asset.FolderId = folder?.Id;

            // EXIF
            if (assetType == AssetType.IMAGE || assetType == AssetType.VIDEO)
            {
                var exif = await _exifService.ExtractExifAsync(physicalPath, ct);
                if (exif != null)
                    asset.Exif = exif;
            }

            // Media tags
            if (asset.Exif != null)
            {
                var detectedTags = await _mediaRecognitionService.DetectMediaTypeAsync(physicalPath, asset.Exif, ct);
                if (detectedTags.Any())
                {
                    asset.Tags = detectedTags.Select(t => new AssetTag
                    {
                        TagType = t,
                        DetectedAt = DateTime.UtcNow
                    }).ToList();
                }
            }

            if (isNew)
                _dbContext.Assets.Add(asset);

            await _dbContext.SaveChangesAsync(ct);

            // Thumbnails
            var thumbnails = await _thumbnailService.GenerateThumbnailsAsync(physicalPath, asset.Id, ct);
            if (thumbnails.Any())
            {
                _dbContext.AssetThumbnails.AddRange(thumbnails);
                await _dbContext.SaveChangesAsync(ct);
            }

            // ML jobs
            if (asset.Type == AssetType.IMAGE && _mediaRecognitionService.ShouldTriggerMlJob(asset, asset.Exif))
            {
                await _mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.FaceDetection, ct);
                await _mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.ObjectRecognition, ct);
            }

            Console.WriteLine($"[INDEX-FILE] Indexed successfully: {storedPath} (id={asset.Id})");
            return asset;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[INDEX-FILE] Error indexing {physicalPath}: {ex.Message}");
            return null;
        }
    }

    private static AssetType DetermineAssetType(string extension) => extension switch
    {
        "jpg" or "jpeg" or "png" or "gif" or "bmp" or "webp" or "tiff" or "tif" or "heic" or "heif" or "raw" or "cr2" or "nef" or "arw" => AssetType.IMAGE,
        "mp4" or "mov" or "avi" or "mkv" or "wmv" or "flv" or "webm" or "m4v" or "3gp" => AssetType.VIDEO,
        _ => AssetType.IMAGE
    };

    private async Task<Folder?> GetOrCreateFolderForPathAsync(string? folderPath, CancellationToken ct)
    {
        if (string.IsNullOrEmpty(folderPath))
            return null;

        var virtualPath = NormalizeVirtualPath(await _settingsService.VirtualizePathAsync(folderPath));
        if (string.IsNullOrEmpty(virtualPath))
            return null;

        var pathChain = new List<string>();
        var current = virtualPath;
        while (!string.IsNullOrEmpty(current))
        {
            pathChain.Add(current);
            var parent = GetParentVirtualPath(current);
            if (string.IsNullOrEmpty(parent) || string.Equals(parent, current, StringComparison.OrdinalIgnoreCase))
                break;
            current = parent;
        }
        pathChain.Reverse();

        var allPaths = pathChain.ToHashSet(StringComparer.OrdinalIgnoreCase);
        var existingFolders = await _dbContext.Folders
            .Where(f => allPaths.Contains(f.Path))
            .ToDictionaryAsync(f => f.Path, f => f, StringComparer.OrdinalIgnoreCase, ct);

        var physicalNormalized = NormalizeVirtualPath(folderPath);
        if (!string.Equals(physicalNormalized, virtualPath, StringComparison.OrdinalIgnoreCase) &&
            !existingFolders.ContainsKey(virtualPath))
        {
            var byPhysical = await _dbContext.Folders
                .FirstOrDefaultAsync(f => f.Path == physicalNormalized, ct);
            if (byPhysical != null)
                existingFolders[physicalNormalized] = byPhysical;
        }

        Folder? parentFolder = null;
        Folder? resultFolder = null;

        foreach (var path in pathChain)
        {
            existingFolders.TryGetValue(path, out var folder);

            if (folder == null &&
                string.Equals(path, virtualPath, StringComparison.OrdinalIgnoreCase) &&
                !string.Equals(physicalNormalized, virtualPath, StringComparison.OrdinalIgnoreCase))
            {
                existingFolders.TryGetValue(physicalNormalized, out folder);
            }

            if (folder != null)
            {
                if (folder.ParentFolderId != parentFolder?.Id ||
                    !string.Equals(folder.Path, path, StringComparison.OrdinalIgnoreCase))
                {
                    folder.Path = path;
                    folder.Name = GetVirtualFolderName(path);
                    folder.ParentFolderId = parentFolder?.Id;
                    await _dbContext.SaveChangesAsync(ct);
                }
            }
            else
            {
                folder = new Folder
                {
                    Path = path,
                    Name = GetVirtualFolderName(path),
                    ParentFolderId = parentFolder?.Id,
                    CreatedAt = DateTime.UtcNow
                };
                _dbContext.Folders.Add(folder);
                await _dbContext.SaveChangesAsync(ct);

                if (!IsPersonalUserPath(path))
                    await AssignAdminOwnerAsync(folder.Id, ct);
            }

            parentFolder = folder;
            resultFolder = folder;
        }

        return resultFolder;
    }

    private async Task<Guid?> GetPrimaryAdminIdAsync(CancellationToken ct)
    {
        var primaryAdmin = await _dbContext.Users
            .Where(u => u.IsPrimaryAdmin)
            .Select(u => (Guid?)u.Id)
            .FirstOrDefaultAsync(ct);

        if (primaryAdmin != null)
            return primaryAdmin;

        // Fallback: first active admin by creation date (in case IsPrimaryAdmin was never set)
        return await _dbContext.Users
            .Where(u => u.Role == "Admin" && u.IsActive)
            .OrderBy(u => u.CreatedAt)
            .Select(u => (Guid?)u.Id)
            .FirstOrDefaultAsync(ct);
    }

    private async Task AssignAdminOwnerAsync(Guid folderId, CancellationToken ct)
    {
        var primaryAdminId = await GetPrimaryAdminIdAsync(ct);
        if (primaryAdminId == null) return;

        var exists = await _dbContext.FolderPermissions
            .AnyAsync(p => p.UserId == primaryAdminId.Value && p.FolderId == folderId, ct);

        if (exists) return;

        _dbContext.FolderPermissions.Add(new FolderPermission
        {
            UserId = primaryAdminId.Value,
            FolderId = folderId,
            CanRead = true,
            CanWrite = true,
            CanDelete = true,
            CanManagePermissions = true,
            GrantedByUserId = primaryAdminId.Value
        });

        await _dbContext.SaveChangesAsync(ct);
    }

    private static bool IsPersonalUserPath(string normalizedPath) =>
        normalizedPath.StartsWith("/assets/users/", StringComparison.OrdinalIgnoreCase);

    private static bool TryGetOwnerIdFromVirtualPath(string path, out Guid ownerId)
    {
        ownerId = Guid.Empty;
        if (string.IsNullOrWhiteSpace(path))
            return false;

        var normalized = path.Replace('\\', '/').TrimStart('/');
        if (!normalized.StartsWith("assets/users/", StringComparison.OrdinalIgnoreCase))
            return false;

        var parts = normalized.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length < 3)
            return false;

        return Guid.TryParse(parts[2], out ownerId);
    }

    private static string NormalizeVirtualPath(string path) =>
        path.Replace('\\', '/').TrimEnd('/');

    private static string GetParentVirtualPath(string path)
    {
        var normalized = NormalizeVirtualPath(path);
        var lastSlash = normalized.LastIndexOf('/');
        return lastSlash <= 0 ? string.Empty : normalized[..lastSlash];
    }

    private static string GetVirtualFolderName(string path)
    {
        var normalized = NormalizeVirtualPath(path);
        var lastSlash = normalized.LastIndexOf('/');
        return lastSlash >= 0 ? normalized[(lastSlash + 1)..] : normalized;
    }
}
