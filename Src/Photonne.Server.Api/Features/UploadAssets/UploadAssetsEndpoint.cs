using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
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
            .RequireAuthorization();
    }

    private async Task<IResult> Handle(
        [FromForm] IFormFile file,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] FileHashService hashService,
        [FromServices] ExifExtractorService exifService,
        [FromServices] ThumbnailGeneratorService thumbnailService,
        [FromServices] MediaRecognitionService mediaRecognitionService,
        [FromServices] IMlJobService mlJobService,
        [FromServices] SettingsService settingsService,
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

        // Guardar los uploads del usuario en su carpeta dedicada dentro del NAS
        var uploadsVirtualPath = $"/assets/users/{userId}/Uploads";
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

            // 5. Create Asset record
            var fileInfo = new FileInfo(targetPath);
            var extension = Path.GetExtension(targetPath).ToLowerInvariant();
            var assetType = GetAssetType(extension);

            // Normalizar FullPath para la BD: si está en la biblioteca gestionada, usamos el prefijo /assets
            var dbPath = await settingsService.VirtualizePathAsync(targetPath);

            var asset = new Asset
            {
                FileName = finalFileName,
                FullPath = dbPath,
                FileSize = fileInfo.Length,
                Checksum = checksum,
                Type = assetType,
                Extension = extension,
                FileCreatedAt = fileInfo.CreationTimeUtc,
                FileModifiedAt = fileInfo.LastWriteTimeUtc,
                ScannedAt = DateTime.UtcNow,
                FolderId = uploadsFolder?.Id,
                OwnerId = userId
            };

            // 6. Extract Metadata & Recognition
            var exif = await exifService.ExtractExifAsync(targetPath, cancellationToken);
            if (exif != null)
            {
                asset.Exif = exif;
                if (exif.DateTimeOriginal != null)
                {
                    asset.FileCreatedAt = exif.DateTimeOriginal.Value;
                    asset.FileModifiedAt = asset.FileCreatedAt;
                }
                var tags = await mediaRecognitionService.DetectMediaTypeAsync(targetPath, exif, cancellationToken);
                if (tags.Any())
                {
                    asset.Tags = tags.Select(t => new AssetTag { TagType = t, DetectedAt = DateTime.UtcNow }).ToList();
                }
            }

            try
            {
                File.SetCreationTimeUtc(targetPath, asset.FileCreatedAt);
                File.SetLastWriteTimeUtc(targetPath, asset.FileModifiedAt);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[WARN] No se pudieron actualizar timestamps del archivo {targetPath}: {ex.Message}");
            }

            // 7. Save to DB
            dbContext.Assets.Add(asset);
            await dbContext.SaveChangesAsync(cancellationToken);

            // 8. Generate Thumbnails
            var thumbnails = await thumbnailService.GenerateThumbnailsAsync(targetPath, asset.Id, cancellationToken);
            if (thumbnails.Any())
            {
                dbContext.AssetThumbnails.AddRange(thumbnails);
            }

            // 9. Queue ML Jobs
            if (mediaRecognitionService.ShouldTriggerMlJob(asset, asset.Exif))
            {
                await mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.FaceDetection, cancellationToken);
                await mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.ObjectRecognition, cancellationToken);
            }

            await dbContext.SaveChangesAsync(cancellationToken);

            return Results.Ok(new { message = "Asset uploaded successfully", assetId = asset.Id });
        }
        catch (Exception ex)
        {
            if (File.Exists(tempPath)) File.Delete(tempPath);
            return Results.Problem(ex.Message);
        }
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
