using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.SyncAsset;

public class SyncAssetEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/sync", Handle)
            .WithName("SyncAsset")
            .WithTags("Assets")
            .WithDescription("Copies a pending asset from the user's MobileBackup source area into the internal assets directory and indexes it inline.")
            .RequireAuthorization()
            .RequireRateLimiting("demo-upload");
    }

    private async Task<IResult> Handle(
        [FromQuery] string path,
        [FromQuery] string? deviceName,
        [FromServices] SettingsService settingsService,
        [FromServices] FileHashService hashService,
        [FromServices] ExifExtractorService exifService,
        [FromServices] AssetIndexingService indexingService,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(path))
            return Results.BadRequest("Path is required");

        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }
            var username = user.GetUsername();
            if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

            // Source must live inside the authenticated user's personal subtree.
            // Prevents one user from triggering /sync against another user's files.
            var userConfiguredPath = settingsService.GetAssetsPath();
            var userRoot = Path.Combine(userConfiguredPath, "users", username);
            if (!IsPathInside(path, userRoot))
                return Results.Forbid();

            if (!File.Exists(path))
                return Results.NotFound("Source file does not exist on disk");

            var currentFileInfo = new FileInfo(path);

            // Enforce global max upload size (ServerSettings.MaxUploadSizeMb). 0 = unlimited.
            var maxUploadRaw = await settingsService.GetSettingAsync(
                "ServerSettings.MaxUploadSizeMb", Guid.Empty, "0");
            if (int.TryParse(maxUploadRaw, out var maxUploadMb) && maxUploadMb > 0)
            {
                var maxBytes = (long)maxUploadMb * 1024L * 1024L;
                if (currentFileInfo.Length > maxBytes)
                {
                    return Results.Problem(
                        detail: $"File exceeds the maximum allowed size ({maxUploadMb} MB).",
                        statusCode: StatusCodes.Status413PayloadTooLarge);
                }
            }

            // Enforce per-user storage quota.
            var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
            if (dbUser?.StorageQuotaBytes.HasValue == true)
            {
                var usedBytes = await dbContext.Assets
                    .Where(a => a.OwnerId == userId && a.DeletedAt == null)
                    .SumAsync(a => (long?)a.FileSize, cancellationToken) ?? 0L;

                if (usedBytes + currentFileInfo.Length > dbUser.StorageQuotaBytes.Value)
                    return Results.Problem(
                        detail: "Storage quota exceeded.",
                        statusCode: StatusCodes.Status409Conflict);
            }

            // Resolve and prepare the destination folder (mirrors /upload's MobileBackup branch).
            // Optional per-device subfolder keeps multi-phone backups visually grouped.
            var mobileBackupVirtual = $"/assets/users/{username}/MobileBackup";
            var sanitizedDevice = DeviceFolderSanitizer.Sanitize(deviceName);
            if (sanitizedDevice != null) mobileBackupVirtual += $"/{sanitizedDevice}";
            var mobileBackupRoot = await settingsService.ResolvePhysicalPathAsync(mobileBackupVirtual);

            await EnsureFolderRecordAsync(dbContext, userId, mobileBackupVirtual, cancellationToken);

            if (!Directory.Exists(mobileBackupRoot))
            {
                Directory.CreateDirectory(mobileBackupRoot);
                Console.WriteLine($"[SYNC] Created MobileBackup directory: {mobileBackupRoot}");
            }

            Console.WriteLine($"[SYNC] Source: {path}");
            Console.WriteLine($"[SYNC] Destination root: {mobileBackupRoot}");

            var fileName = currentFileInfo.Name;
            var relativePath = Path.GetRelativePath(userConfiguredPath, path);
            var targetPath = Path.Combine(mobileBackupRoot, relativePath);
            var targetDirectory = Path.GetDirectoryName(targetPath);
            if (!string.IsNullOrEmpty(targetDirectory))
            {
                Directory.CreateDirectory(targetDirectory);
            }

            // No-op when the source is already inside the destination directory.
            var normalizedPath = Path.GetFullPath(path);
            var normalizedLibraryPath = Path.GetFullPath(mobileBackupRoot);
            if (normalizedPath.StartsWith(normalizedLibraryPath, StringComparison.OrdinalIgnoreCase))
            {
                Console.WriteLine($"[SYNC] File is already inside the destination directory: {path}");
                return Results.Ok(new
                {
                    message = "File is already in the destination directory",
                    targetPath = await settingsService.VirtualizePathAsync(path)
                });
            }

            // Checksum-based dedup against the whole library.
            var sourceChecksum = await hashService.CalculateFileHashAsync(path, cancellationToken);

            var existingAsset = await dbContext.Assets
                .FirstOrDefaultAsync(a => a.Checksum == sourceChecksum, cancellationToken);
            if (existingAsset != null)
            {
                var existingPhysicalPath = await settingsService.ResolvePhysicalPathAsync(existingAsset.FullPath);
                if (!string.IsNullOrEmpty(existingPhysicalPath) && File.Exists(existingPhysicalPath))
                {
                    Console.WriteLine($"[SYNC] Asset with same checksum already exists: {existingPhysicalPath}");
                    return Results.Ok(new
                    {
                        message = "Asset already exists (same content)",
                        assetId = existingAsset.Id,
                        targetPath = existingAsset.FullPath
                    });
                }
            }

            // Handle filename collisions at the destination.
            if (File.Exists(targetPath))
            {
                string existingChecksum;
                try
                {
                    existingChecksum = await hashService.CalculateFileHashAsync(targetPath, cancellationToken);
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[SYNC] Warning: could not hash existing file {targetPath}: {ex.Message}");
                    existingChecksum = string.Empty;
                }

                if (existingChecksum == sourceChecksum)
                {
                    Console.WriteLine($"[SYNC] File with same name and checksum already at destination: {targetPath}");
                    return Results.Ok(new
                    {
                        message = "Asset already exists at destination",
                        targetPath = await settingsService.VirtualizePathAsync(targetPath)
                    });
                }

                // Different checksum → preserve both by giving the new copy a unique name.
                targetPath = Path.Combine(mobileBackupRoot, $"{Guid.NewGuid()}_{fileName}");
            }

            // Pick the best available creation timestamp (EXIF DateTimeOriginal beats filesystem mtime).
            var originalCreation = currentFileInfo.CreationTimeUtc;
            var originalLastWrite = currentFileInfo.LastWriteTimeUtc;
            try
            {
                var exif = await exifService.ExtractExifAsync(path, cancellationToken);
                if (exif?.DateTimeOriginal != null)
                {
                    originalCreation = exif.DateTimeOriginal.Value;
                    originalLastWrite = exif.DateTimeOriginal.Value;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[SYNC] Warning: could not extract EXIF from {path}: {ex.Message}");
            }

            Console.WriteLine($"[SYNC] Copying {path} → {targetPath}");
            File.Copy(path, targetPath, overwrite: false);

            if (!File.Exists(targetPath))
            {
                throw new Exception($"File was not copied to {targetPath}");
            }

            // Cryptographic verification: hash the destination and compare against the
            // source hash already computed for dedup. This is the "100% backup" guarantee
            // — silent corruption (cross-filesystem copy, disk error, truncated write)
            // would have left the indexed Asset row pointing at a file that doesn't
            // match its recorded checksum. If they differ we delete the bad copy and
            // surface a 500 so the client retries instead of trusting partial data.
            var targetChecksum = await hashService.CalculateFileHashAsync(targetPath, cancellationToken);
            if (!string.Equals(targetChecksum, sourceChecksum, StringComparison.OrdinalIgnoreCase))
            {
                Console.WriteLine(
                    $"[SYNC ERROR] Checksum mismatch after copy: source={sourceChecksum} target={targetChecksum} path={targetPath}");
                try { File.Delete(targetPath); } catch { /* best-effort cleanup */ }
                return Results.Problem(
                    detail: "Checksum mismatch between source and destination after copy.",
                    statusCode: StatusCodes.Status500InternalServerError);
            }

            File.SetCreationTimeUtc(targetPath, originalCreation);
            File.SetLastWriteTimeUtc(targetPath, originalLastWrite);

            // Inline indexing. If indexing aborted before any DB row was created the copy is a
            // true orphan and we remove it. Phase B will replace inline indexing with the
            // enrichment-task worker so partial failures are tracked per task.
            var indexed = await indexingService.IndexFileAsync(targetPath, userId, cancellationToken);
            if (indexed == null)
            {
                try { File.Delete(targetPath); } catch { /* best-effort cleanup */ }
                return Results.Problem(
                    detail: "Failed to index synced asset.",
                    statusCode: StatusCodes.Status500InternalServerError);
            }

            return Results.Ok(new
            {
                message = "Asset synced and indexed",
                assetId = indexed.Id,
                targetPath = indexed.FullPath
            });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SYNC ERROR] {ex.Message}");
            Console.WriteLine($"[SYNC ERROR] {ex.StackTrace}");
            return Results.Problem($"Failed to sync asset: {ex.Message}");
        }
    }

    private static bool IsPathInside(string path, string root)
    {
        var fullPath = Path.GetFullPath(path);
        var fullRoot = Path.GetFullPath(root);
        if (!fullRoot.EndsWith(Path.DirectorySeparatorChar))
            fullRoot += Path.DirectorySeparatorChar;
        return fullPath.StartsWith(fullRoot, StringComparison.OrdinalIgnoreCase);
    }

    private static async Task EnsureFolderRecordAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string folderPath,
        CancellationToken cancellationToken)
    {
        var normalizedPath = folderPath.Replace('\\', '/').TrimEnd('/');
        if (string.IsNullOrEmpty(normalizedPath))
        {
            return;
        }

        var existing = await dbContext.Folders
            .FirstOrDefaultAsync(f => f.Path == normalizedPath, cancellationToken);
        if (existing != null)
        {
            return;
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
