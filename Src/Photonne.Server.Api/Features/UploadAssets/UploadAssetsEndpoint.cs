using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.UploadAssets;

public class UploadAssetsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/upload", Handle)
            .DisableAntiforgery()
            .WithName("UploadAsset")
            .WithTags("Assets")
            .WithDescription("Uploads an asset to the internal storage and indexes it")
            .RequireAuthorization()
            .RequireRateLimiting("demo-upload");
    }

    private const string DefaultDestinationFolder = "Uploads";
    private const string MobileBackupDestinationFolder = "MobileBackup";

    private async Task<IResult> Handle(
        [FromForm] IFormFile file,
        [FromForm] string? destination,
        [FromForm] string? deviceName,
        [FromForm] string? fileModifiedAt,
        [FromForm] string? fileCreatedAt,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] FileHashService hashService,
        [FromServices] IEnrichmentService enrichmentService,
        [FromServices] SettingsService settingsService,
        [FromServices] ILogger<UploadAssetsEndpoint> logger,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (file == null || file.Length == 0)
            return Results.BadRequest("No file uploaded");

        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return Results.Unauthorized();
        }
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        // Validar tamaño máximo global (ServerSettings.MaxUploadSizeMb). 0 = sin límite.
        var maxUploadRaw = await settingsService.GetSettingAsync(
            "ServerSettings.MaxUploadSizeMb", Guid.Empty, "0");
        if (int.TryParse(maxUploadRaw, out var maxUploadMb) && maxUploadMb > 0)
        {
            var maxBytes = (long)maxUploadMb * 1024L * 1024L;
            if (file.Length > maxBytes)
            {
                return Results.Problem(
                    detail: $"El archivo supera el tamaño máximo permitido ({maxUploadMb} MB).",
                    statusCode: StatusCodes.Status413PayloadTooLarge);
            }
        }

        // Validar cuota de almacenamiento
        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser?.StorageQuotaBytes.HasValue == true)
        {
            var usedBytes = await dbContext.Assets
                .Where(a => a.OwnerId == userId && a.DeletedAt == null)
                .SumAsync(a => (long?)a.FileSize, cancellationToken) ?? 0L;

            if (usedBytes + file.Length > dbUser.StorageQuotaBytes.Value)
                return Results.Problem(
                    detail: "Has alcanzado el límite de almacenamiento asignado.",
                    statusCode: StatusCodes.Status409Conflict);
        }

        // Resolve the destination folder. Manual uploads always go to /Uploads.
        // Mobile backups go to /MobileBackup, optionally with a per-device subfolder
        // (/MobileBackup/{deviceName}/) so the same user backing up multiple phones
        // keeps them visually separated.
        var uploadsVirtualPath = ResolveDestinationVirtualPath(username, destination, deviceName);
        var uploadsRootPath = await settingsService.ResolvePhysicalPathAsync(uploadsVirtualPath);

        var uploadsFolder = await EnsureFolderRecordAsync(dbContext, userId, uploadsVirtualPath, cancellationToken);

        if (!Directory.Exists(uploadsRootPath))
            Directory.CreateDirectory(uploadsRootPath);

        // 1. Save file to temporary location to calculate hash
        var tempPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString() + Path.GetExtension(file.FileName));
        try
        {
            using (var stream = new FileStream(tempPath, FileMode.Create))
            {
                await file.CopyToAsync(stream, cancellationToken);
            }

            // 2. Calculate hash
            var checksum = await hashService.CalculateFileHashAsync(tempPath, cancellationToken);

            // 3. Check if it already exists
            var existingAsset = await dbContext.Assets.FirstOrDefaultAsync(a => a.Checksum == checksum, cancellationToken);
            if (existingAsset != null)
            {
                File.Delete(tempPath);
                return Results.Ok(new { message = "Asset already exists", assetId = existingAsset.Id });
            }

            // 4. Move to final destination (Managed Library)
            var finalFileName = file.FileName;
            var targetPath = Path.Combine(uploadsRootPath, finalFileName);

            // Handle filename collisions
            if (File.Exists(targetPath))
            {
                finalFileName = $"{Guid.NewGuid()}_{file.FileName}";
                targetPath = Path.Combine(uploadsRootPath, finalFileName);
            }

            File.Move(tempPath, targetPath);

            // Restore the original device timestamps the client sent along —
            // otherwise the moved temp file carries the upload time and the
            // library loses the real mtime forever. Same pattern as
            // SyncAssetEndpoint. SetCreationTimeUtc is a silent no-op on
            // filesystems without birth time; the Asset row below uses the
            // parsed values directly so the database stays correct anyway.
            var modifiedUtc = ParseClientTimestamp(fileModifiedAt);
            var createdUtc = ParseClientTimestamp(fileCreatedAt);
            if (modifiedUtc.HasValue) File.SetLastWriteTimeUtc(targetPath, modifiedUtc.Value);
            if (createdUtc.HasValue) File.SetCreationTimeUtc(targetPath, createdUtc.Value);

            // 5. Create Asset record with the minimum the backup contract requires:
            // file on disk + Asset row + checksum + owner/folder. Enrichment
            // (EXIF, thumbnails, media tags, ML) is enqueued and runs in the
            // background — failure there does NOT fail the upload.
            var fileInfo = new FileInfo(targetPath);
            var extension = Path.GetExtension(targetPath).ToLowerInvariant();
            var assetType = GetAssetType(extension);
            var dbPath = await settingsService.VirtualizePathAsync(targetPath);

            var asset = new Asset
            {
                FileName = finalFileName,
                FullPath = dbPath,
                FileSize = fileInfo.Length,
                Checksum = checksum,
                Type = assetType,
                Extension = extension,
                FileCreatedAt = createdUtc ?? fileInfo.CreationTimeUtc,
                FileModifiedAt = modifiedUtc ?? fileInfo.LastWriteTimeUtc,
                // Seed CapturedAt from the client-supplied timestamps (the
                // device's original dates) falling back to the filesystem;
                // CapturedAtSource stays FileSystem so the EXIF enrichment
                // worker overwrites it with DateTimeOriginal as soon as it
                // runs.
                CapturedAt = createdUtc ?? modifiedUtc ?? fileInfo.CreationTimeUtc,
                ScannedAt = DateTime.UtcNow,
                FolderId = uploadsFolder?.Id,
                OwnerId = userId
            };

            dbContext.Assets.Add(asset);
            await dbContext.SaveChangesAsync(cancellationToken);

            // 6. Enqueue the full enrichment pipeline.
            if (assetType == AssetType.Image || assetType == AssetType.Video)
            {
                await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Exif, cancellationToken);
                await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.MediaRecognition, cancellationToken);
            }
            await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails, cancellationToken);
            // ML tasks gate themselves at execution time (image type + size), so it's
            // fine to enqueue them eagerly here — the worker dispatch will short-circuit
            // for video or tiny assets.

            return Results.Ok(new { message = "Asset uploaded successfully", assetId = asset.Id });
        }
        catch (Exception ex)
        {
            // Log the full exception server-side; the client only gets the
            // message via ProblemDetails, which is not enough to diagnose
            // I/O or database failures after the fact.
            logger.LogError(ex, "Upload failed for user {Username}, file {FileName}", username, file.FileName);
            if (File.Exists(tempPath)) File.Delete(tempPath);
            return Results.Problem(ex.Message);
        }
    }

    /// <summary>
    /// Parses a client-supplied epoch-milliseconds timestamp form field.
    /// Returns null for missing, malformed, or implausible values (before
    /// the Unix epoch or more than 5 minutes in the future — clock skew
    /// tolerance) so a buggy or malicious client can't poison file dates.
    /// </summary>
    internal static DateTime? ParseClientTimestamp(string? epochMillis)
    {
        if (string.IsNullOrWhiteSpace(epochMillis)) return null;
        if (!long.TryParse(epochMillis, out var millis) || millis <= 0) return null;

        DateTime parsed;
        try
        {
            parsed = DateTimeOffset.FromUnixTimeMilliseconds(millis).UtcDateTime;
        }
        catch (ArgumentOutOfRangeException)
        {
            return null;
        }

        return parsed > DateTime.UtcNow.AddMinutes(5) ? null : parsed;
    }

    private static string ResolveDestinationFolder(string? destination)
    {
        if (string.IsNullOrWhiteSpace(destination)) return DefaultDestinationFolder;
        return destination.Trim().ToLowerInvariant() switch
        {
            "mobile-backup" => MobileBackupDestinationFolder,
            "uploads" => DefaultDestinationFolder,
            _ => DefaultDestinationFolder
        };
    }

    /// <summary>
    /// Builds the per-user virtual destination path. The optional
    /// <paramref name="deviceName"/> only takes effect for the MobileBackup
    /// destination — manual /Uploads is always flat. Sanitizes the device name
    /// before using it as a path segment so a malicious or messy client can't
    /// inject path traversal or create absurd directory names.
    /// </summary>
    internal static string ResolveDestinationVirtualPath(string username, string? destination, string? deviceName)
    {
        var folder = ResolveDestinationFolder(destination);
        var basePath = $"/assets/users/{username}/{folder}";

        if (folder != MobileBackupDestinationFolder) return basePath;

        var sanitized = DeviceFolderSanitizer.Sanitize(deviceName);
        return sanitized == null ? basePath : $"{basePath}/{sanitized}";
    }

    private AssetType GetAssetType(string extension)
    {
        var imageExtensions = new[] { ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".gif", ".webp", ".heic", ".heif" };
        return imageExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase) ? AssetType.Image : AssetType.Video;
    }

    private static async Task<Folder?> EnsureFolderRecordAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string folderPath,
        CancellationToken cancellationToken)
    {
        var normalizedPath = folderPath.Replace('\\', '/').TrimEnd('/');
        if (string.IsNullOrEmpty(normalizedPath))
        {
            return null;
        }

        var existing = await dbContext.Folders
            .FirstOrDefaultAsync(f => f.Path == normalizedPath, cancellationToken);
        if (existing != null)
        {
            await EnsureFolderPermissionAsync(dbContext, userId, existing.Id, cancellationToken);
            return existing;
        }

        var parentPath = Path.GetDirectoryName(normalizedPath)?.Replace('\\', '/').TrimEnd('/');
        Folder? parentFolder = null;
        if (!string.IsNullOrEmpty(parentPath))
        {
            parentFolder = await dbContext.Folders
                .FirstOrDefaultAsync(f => f.Path == parentPath, cancellationToken);

            if (parentFolder == null)
            {
                parentFolder = new Folder
                {
                    Path = parentPath,
                    Name = Path.GetFileName(parentPath),
                    ParentFolderId = null
                };
                dbContext.Folders.Add(parentFolder);
                await dbContext.SaveChangesAsync(cancellationToken);

                await EnsureFolderPermissionAsync(dbContext, userId, parentFolder.Id, cancellationToken);
            }
        }

        var folder = new Folder
        {
            Path = normalizedPath,
            Name = Path.GetFileName(normalizedPath),
            ParentFolderId = parentFolder?.Id
        };

        dbContext.Folders.Add(folder);
        await dbContext.SaveChangesAsync(cancellationToken);

        await EnsureFolderPermissionAsync(dbContext, userId, folder.Id, cancellationToken);
        return folder;
    }

    private static async Task EnsureFolderPermissionAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        Guid folderId,
        CancellationToken cancellationToken)
    {
        var exists = await dbContext.FolderPermissions
            .AnyAsync(p => p.UserId == userId && p.FolderId == folderId, cancellationToken);
        if (exists)
        {
            return;
        }

        // Inherited semantics: personal-space folders and folders already
        // covered by an ancestor grant don't need their own row.
        if (await FolderPermissionGuard.IsRedundantFullGrantAsync(dbContext, userId, folderId, cancellationToken))
        {
            return;
        }

        dbContext.FolderPermissions.Add(new FolderPermission
        {
            UserId = userId,
            FolderId = folderId,
            CanRead = true,
            CanWrite = true,
            CanDelete = true,
            CanManagePermissions = true,
            GrantedByUserId = userId
        });
        await dbContext.SaveChangesAsync(cancellationToken);
    }
}
