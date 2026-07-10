using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class AssetIndexingService
{
    private readonly FileHashService _hashService;
    private readonly IEnrichmentService _enrichmentService;
    private readonly MediaRecognitionService _mediaRecognitionService;
    private readonly SettingsService _settingsService;
    private readonly ApplicationDbContext _dbContext;

    public AssetIndexingService(
        FileHashService hashService,
        IEnrichmentService enrichmentService,
        MediaRecognitionService mediaRecognitionService,
        SettingsService settingsService,
        ApplicationDbContext dbContext)
    {
        _hashService = hashService;
        _enrichmentService = enrichmentService;
        _mediaRecognitionService = mediaRecognitionService;
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
                await RefreshFileSnapshotIfChangedAsync(existingByPath, physicalPath, ct);
                await EnqueueMissingEnrichmentAsync(existingByPath, ct);
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
                    await RefreshFileSnapshotIfChangedAsync(existingByChecksum, physicalPath, ct);
                    await EnqueueMissingEnrichmentAsync(existingByChecksum, ct);
                    return existingByChecksum;
                }
            }

            var fileInfo = new FileInfo(physicalPath);
            var extension = fileInfo.Extension.TrimStart('.').ToLowerInvariant();
            var assetType = DetermineAssetType(extension);

            // Linux hosts rewrite the birthtime when files are copied between
            // volumes (rsync preserves only mtime), so clamp the creation date
            // to the older of the two timestamps — same guard as
            // DirectoryScanner, which this path previously ignored.
            var createdUtc = fileInfo.CreationTimeUtc;
            var modifiedUtc = fileInfo.LastWriteTimeUtc;
            var effectiveCreatedUtc = createdUtc > modifiedUtc ? modifiedUtc : createdUtc;

            Asset asset;
            bool isNew;

            if (existingByPath != null)
            {
                // Update existing
                existingByPath.Checksum = checksum;
                existingByPath.FileSize = fileInfo.Length;
                existingByPath.FileModifiedAt = fileInfo.LastWriteTimeUtc;
                existingByPath.IsFileMissing = false;
                asset = existingByPath;
                isNew = false;
            }
            else if (existingByChecksum != null)
            {
                // File moved/renamed — update path
                existingByChecksum.FullPath = storedPath;
                existingByChecksum.FileName = fileInfo.Name;
                existingByChecksum.FileModifiedAt = fileInfo.LastWriteTimeUtc;
                asset = existingByChecksum;
                isNew = false;
            }
            else
            {
                var seedTz = await MetadataTimeZone.ResolveAsync(_settingsService);
                asset = new Asset
                {
                    FileName = fileInfo.Name,
                    FullPath = storedPath,
                    FileSize = fileInfo.Length,
                    Checksum = checksum,
                    Type = assetType,
                    Extension = extension,
                    FileCreatedAt = effectiveCreatedUtc,
                    FileModifiedAt = modifiedUtc,
                    // Seed CapturedAt from the filesystem timestamp (genuine UTC)
                    // as local wall-clock — the same frame EXIF dates use; the
                    // enrichment worker overwrites it with DateTimeOriginal as
                    // soon as it runs.
                    CapturedAt = MetadataTimeZone.ToLocalWallClock(effectiveCreatedUtc, seedTz),
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
                // Owner from path — validate that the extracted username actually exists in the DB.
                // If not, fall back to the primary admin to avoid FK constraint violations.
                if (UserStorageService.TryGetUsernameFromPath(storedPath, out var ownerUsername))
                {
                    var resolvedOwner = await _dbContext.Users
                        .AsNoTracking()
                        .Where(u => u.Username == ownerUsername)
                        .Select(u => (Guid?)u.Id)
                        .FirstOrDefaultAsync(ct);
                    if (resolvedOwner.HasValue)
                    {
                        asset.OwnerId = resolvedOwner.Value;
                    }
                    else
                    {
                        var primaryAdmin = await GetPrimaryAdminIdAsync(ct);
                        asset.OwnerId = primaryAdmin;
                        Console.WriteLine($"[INDEX-FILE] Owner '{ownerUsername}' not found, assigned to primary admin ({primaryAdmin}).");
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
            var folder = await GetOrCreateFolderForPathAsync(folderPath, externalLibraryId, ct);
            asset.FolderId = folder?.Id;

            if (isNew)
                _dbContext.Assets.Add(asset);

            await _dbContext.SaveChangesAsync(ct);

            // Backup contract satisfied: file + Asset row + checksum are persisted.
            // Enqueue the enrichment tasks; the worker runs EXIF/thumbnails/media
            // recognition/ML in the background. We don't gate the return on them.
            await EnqueueEnrichmentAsync(asset, ct);

            Console.WriteLine($"[INDEX-FILE] Indexed successfully: {storedPath} (id={asset.Id})");
            return asset;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[INDEX-FILE] Error indexing {physicalPath}: {ex.Message}");
            return null;
        }
    }

    /// <summary>
    /// Keeps the cheap filesystem facts (dates, size) of an already-indexed
    /// asset in sync with the disk — no re-hash, one UPDATE only when something
    /// actually changed. Without this, the early-return above left the date
    /// columns frozen at first-index time forever, so a later rsync that
    /// restored correct mtimes never reached the DB and every
    /// CapturedAt-from-file fallback kept using stale dates.
    /// </summary>
    private async Task RefreshFileSnapshotIfChangedAsync(Asset asset, string physicalPath, CancellationToken ct)
    {
        var fileInfo = new FileInfo(physicalPath);
        var createdUtc = fileInfo.CreationTimeUtc;
        var modifiedUtc = fileInfo.LastWriteTimeUtc;
        var effectiveCreatedUtc = createdUtc > modifiedUtc ? modifiedUtc : createdUtc;

        // Sub-second tolerance: Postgres stores microseconds while FileInfo
        // reports 100ns ticks — strict equality would force a no-op UPDATE on
        // every single scan.
        static bool SameInstant(DateTime a, DateTime b) => Math.Abs((a - b).TotalSeconds) < 1;
        if (SameInstant(asset.FileModifiedAt, modifiedUtc) &&
            SameInstant(asset.FileCreatedAt, effectiveCreatedUtc) &&
            asset.FileSize == fileInfo.Length)
        {
            return;
        }

        asset.RefreshFileDates(createdUtc, modifiedUtc);
        asset.FileSize = fileInfo.Length;
        await _dbContext.SaveChangesAsync(ct);
    }

    /// <summary>
    /// Enqueues the full enrichment pipeline (EXIF + Thumbnails + MediaRecognition
    /// + missing ML tasks) for a freshly-indexed asset.
    /// </summary>
    private async Task EnqueueEnrichmentAsync(Asset asset, CancellationToken ct)
    {
        if (asset.Type == AssetType.Image || asset.Type == AssetType.Video)
        {
            await _enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Exif, ct);
        }
        await _enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails, ct);
        if (asset.Type == AssetType.Image || asset.Type == AssetType.Video)
        {
            await _enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.MediaRecognition, ct);
        }
        await EnqueueMissingEnrichmentAsync(asset, ct);
    }

    // Enqueues MediaRecognition plus the ML task types whose *CompletedAt is null.
    // Safe to call on re-scans: a previously-failed task will be re-queued (the scan
    // is a manual admin action and reintents are desirable), while completed ML types
    // are skipped. MediaRecognition is (re)queued unconditionally for media assets —
    // it has no *CompletedAt column and its result depends on sibling files on disk,
    // so a rescan after a Live Photo's paired clip lands is the only way the still/
    // clip ever get tagged LivePhoto/MotionPhotoPart. The task is cheap and idempotent
    // (the worker deletes existing tags and recomputes), and EnqueueAsync de-dups any
    // still-pending row so the fresh-index path doesn't double-queue it.
    private async Task EnqueueMissingEnrichmentAsync(Asset asset, CancellationToken ct)
    {
        if (asset.Type == AssetType.Image || asset.Type == AssetType.Video)
        {
            await _enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.MediaRecognition, ct);
        }

        // Reload Exif if not yet attached — needed by the gating in GetMissingMlTaskTypes.
        if (asset.Exif == null)
        {
            asset.Exif = await _dbContext.AssetExifs
                .AsNoTracking()
                .FirstOrDefaultAsync(e => e.AssetId == asset.Id, ct);
        }

        var missing = _mediaRecognitionService.GetMissingMlTaskTypes(asset, asset.Exif);
        foreach (var taskType in missing)
        {
            await _enrichmentService.EnqueueAsync(asset.Id, taskType, ct);
        }
    }

    private static AssetType DetermineAssetType(string extension) => extension switch
    {
        "jpg" or "jpeg" or "png" or "gif" or "bmp" or "webp" or "tiff" or "tif" or "heic" or "heif"
            or "raw" or "cr2" or "cr3" or "nef" or "arw" or "dng" or "orf" or "rw2" or "pef" or "raf" or "srw" => AssetType.Image,
        "mp4" or "mov" or "avi" or "mkv" or "wmv" or "flv" or "webm" or "m4v" or "3gp" => AssetType.Video,
        _ => AssetType.Image
    };

    /// <summary>
    /// Resolves the owner and folder for a file at <paramref name="physicalPath"/>
    /// using the same rules as <see cref="IndexFileAsync"/> — owner from the
    /// personal-path username (or primary admin), folder via
    /// <see cref="GetOrCreateFolderForPathAsync"/>. Shared so the parallel
    /// "unsupported files" catalogue assigns ownership/scoping identically.
    /// <paramref name="storedPath"/> is the virtual/stored path of the file.
    /// </summary>
    public async Task<(Guid? OwnerId, Guid? FolderId)> ResolveOwnerAndFolderAsync(
        string physicalPath,
        string storedPath,
        Guid userId,
        Guid? externalLibraryId,
        CancellationToken ct)
    {
        Guid? ownerId;
        if (externalLibraryId.HasValue)
        {
            ownerId = userId;
        }
        else if (UserStorageService.TryGetUsernameFromPath(storedPath, out var ownerUsername))
        {
            ownerId = await _dbContext.Users
                .AsNoTracking()
                .Where(u => u.Username == ownerUsername)
                .Select(u => (Guid?)u.Id)
                .FirstOrDefaultAsync(ct)
                ?? await GetPrimaryAdminIdAsync(ct);
        }
        else
        {
            ownerId = await GetPrimaryAdminIdAsync(ct);
        }

        var folder = await GetOrCreateFolderForPathAsync(Path.GetDirectoryName(physicalPath), externalLibraryId, ct);
        return (ownerId, folder?.Id);
    }

    private async Task<Folder?> GetOrCreateFolderForPathAsync(string? folderPath, Guid? externalLibraryId, CancellationToken ct)
    {
        if (string.IsNullOrEmpty(folderPath))
            return null;

        var virtualPath = NormalizeVirtualPath(await _settingsService.VirtualizePathAsync(folderPath));
        if (string.IsNullOrEmpty(virtualPath))
            return null;

        // Determine the library root path so we only tag folders at or below it
        string? libraryRootVirtual = null;
        if (externalLibraryId.HasValue)
        {
            var library = await _dbContext.ExternalLibraries
                .AsNoTracking()
                .FirstOrDefaultAsync(l => l.Id == externalLibraryId.Value, ct);
            if (library != null)
                libraryRootVirtual = NormalizeVirtualPath(await _settingsService.VirtualizePathAsync(library.Path));
        }

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
                var needsSave = folder.ParentFolderId != parentFolder?.Id ||
                                !string.Equals(folder.Path, path, StringComparison.OrdinalIgnoreCase);

                if (needsSave)
                {
                    folder.Path = path;
                    folder.Name = GetVirtualFolderName(path);
                    folder.ParentFolderId = parentFolder?.Id;
                }

                // Tag with library only if at/below the library root and not already tagged for a different library
                if (externalLibraryId.HasValue && IsWithinLibraryRoot(path, libraryRootVirtual))
                {
                    if (folder.ExternalLibraryId == null || folder.ExternalLibraryId == externalLibraryId)
                    {
                        folder.ExternalLibraryId = externalLibraryId;
                        needsSave = true;
                    }
                }

                if (needsSave)
                    await _dbContext.SaveChangesAsync(ct);
            }
            else
            {
                folder = new Folder
                {
                    Path = path,
                    Name = GetVirtualFolderName(path),
                    ParentFolderId = parentFolder?.Id,
                    CreatedAt = DateTime.UtcNow,
                    ExternalLibraryId = externalLibraryId.HasValue && IsWithinLibraryRoot(path, libraryRootVirtual)
                        ? externalLibraryId
                        : null
                };
                _dbContext.Folders.Add(folder);
                await _dbContext.SaveChangesAsync(ct);

                if (!IsPersonalUserPath(path) && !VirtualPath.IsStructuralContainer(path))
                    await AssignAdminOwnerAsync(folder.Id, ct);
            }

            parentFolder = folder;
            resultFolder = folder;
        }

        return resultFolder;
    }

    private static bool IsWithinLibraryRoot(string path, string? libraryRootVirtual)
    {
        if (string.IsNullOrEmpty(libraryRootVirtual))
            return true; // No root info → tag everything (fallback)

        return VirtualPath.IsUnder(path, libraryRootVirtual);
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

        // Inherited semantics: indexing /assets/shared/A/B/C materializes one
        // row on the share root (A) only — the rest of the subtree inherits.
        if (await Authorization.FolderPermissionGuard.IsRedundantFullGrantAsync(
                _dbContext, primaryAdminId.Value, folderId, ct))
        {
            return;
        }

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
