using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Dtos;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Processing;
using SixLabors.ImageSharp.Formats.Jpeg;

namespace Photonne.Server.Api.Features.AssetDetail;

public class AssetPendingEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/pending/detail", HandleDetail)
            .WithName("GetPendingAssetDetail")
            .WithTags("Assets")
            .WithDescription("Gets detailed information about a pending asset from the filesystem")
            .RequireAuthorization();

        app.MapGet("/api/assets/pending/content", HandleContent)
            .WithName("GetPendingAssetContent")
            .WithTags("Assets")
            .WithDescription("Gets the original content of a pending asset (image or video)")
            .RequireAuthorization();

        app.MapGet("/api/assets/pending/thumbnail", HandleThumbnail)
            .WithName("GetPendingAssetThumbnail")
            .WithTags("Assets")
            .WithDescription("Gets a thumbnail for a pending asset (image or video)")
            .RequireAuthorization();
    }

    private async Task<IResult> HandleDetail(
        [FromQuery] string path,
        [FromServices] SettingsService settingsService,
        [FromServices] ExifExtractorService exifService,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(path))
            return Results.BadRequest("Path is required");

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(path);
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var userAssetsPath = await settingsService.GetAssetsPathAsync(userId);
        var internalAssetsPath = settingsService.GetInternalAssetsPath();
        
        // Determinar si el archivo está en el directorio del usuario o en el interno
        var normalizedPhysicalPath = Path.GetFullPath(physicalPath);
        var normalizedUserPath = Path.GetFullPath(userAssetsPath);
        var normalizedInternalPath = Path.GetFullPath(internalAssetsPath);
        
        AssetSyncStatus syncStatus;
        if (normalizedPhysicalPath.StartsWith(normalizedInternalPath, StringComparison.OrdinalIgnoreCase))
        {
            // El archivo está en el directorio interno, está copiado pero no indexado
            syncStatus = AssetSyncStatus.Copied;
        }
        else if (normalizedPhysicalPath.StartsWith(normalizedUserPath, StringComparison.OrdinalIgnoreCase))
        {
            // El archivo está en el directorio del usuario, está pendiente
            syncStatus = AssetSyncStatus.Pending;
        }
        else
        {
            return Results.Forbid();
        }

        if (!File.Exists(physicalPath))
            return Results.NotFound("File not found");

        var fileInfo = new FileInfo(physicalPath);
        var extension = Path.GetExtension(physicalPath).ToLowerInvariant();
        var type = GetAssetType(extension);

        var exif = await exifService.ExtractExifAsync(physicalPath, cancellationToken);

        var response = new AssetDetailResponse
        {
            Id = Guid.Empty,
            FileName = fileInfo.Name,
            FullPath = path, // Mantener la ruta original (podría ser virtual) para el cliente
            FileSize = fileInfo.Length,
            FileCreatedAt = fileInfo.CreationTimeUtc,
            FileModifiedAt = fileInfo.LastWriteTimeUtc,
            Extension = extension,
            ScannedAt = DateTime.MinValue,
            Type = type.ToString(),
            Checksum = string.Empty,
            HasExif = exif != null,
            HasThumbnails = false,
            SyncStatus = syncStatus,
            Exif = exif != null ? new ExifDataResponse
            {
                DateTaken = exif.DateTimeOriginal,
                CameraMake = exif.CameraMake,
                CameraModel = exif.CameraModel,
                Width = exif.Width,
                Height = exif.Height,
                Orientation = exif.Orientation,
                Latitude = exif.Latitude,
                Longitude = exif.Longitude,
                Altitude = exif.Altitude,
                Iso = exif.Iso,
                Aperture = exif.Aperture,
                ShutterSpeed = exif.ShutterSpeed,
                FocalLength = exif.FocalLength,
                Description = exif.Description,
                Keywords = exif.Keywords
            } : null
        };

        return Results.Ok(response);
    }

    private async Task<IResult> HandleContent(
        [FromQuery] string path,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(path))
            return Results.BadRequest("Path is required");

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(path);
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var userAssetsPath = await settingsService.GetAssetsPathAsync(userId);
        var internalAssetsPath = settingsService.GetInternalAssetsPath();
        
        // Verificar que el path es seguro (está en el directorio del usuario o interno)
        var normalizedPhysicalPath = Path.GetFullPath(physicalPath);
        var normalizedUserPath = Path.GetFullPath(userAssetsPath);
        var normalizedInternalPath = Path.GetFullPath(internalAssetsPath);
        
        if (!normalizedPhysicalPath.StartsWith(normalizedUserPath, StringComparison.OrdinalIgnoreCase) &&
            !normalizedPhysicalPath.StartsWith(normalizedInternalPath, StringComparison.OrdinalIgnoreCase))
        {
            return Results.Forbid();
        }

        if (!File.Exists(physicalPath))
            return Results.NotFound("File not found");

        var extension = Path.GetExtension(physicalPath).ToLowerInvariant();
        var type = GetAssetType(extension);
        var contentType = GetContentType(extension, type);

        return Results.File(physicalPath, contentType, enableRangeProcessing: true);
    }

    private async Task<IResult> HandleThumbnail(
        [FromQuery] string path,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        [FromQuery] string size = "Medium",
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrEmpty(path))
            return Results.BadRequest("Path is required");

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(path);
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var userAssetsPath = await settingsService.GetAssetsPathAsync(userId);
        var internalAssetsPath = settingsService.GetInternalAssetsPath();
        
        // Verificar que el path es seguro (está en el directorio del usuario o interno)
        var normalizedPhysicalPath = Path.GetFullPath(physicalPath);
        var normalizedUserPath = Path.GetFullPath(userAssetsPath);
        var normalizedInternalPath = Path.GetFullPath(internalAssetsPath);
        
        if (!normalizedPhysicalPath.StartsWith(normalizedUserPath, StringComparison.OrdinalIgnoreCase) &&
            !normalizedPhysicalPath.StartsWith(normalizedInternalPath, StringComparison.OrdinalIgnoreCase))
        {
            return Results.Forbid();
        }

        if (!File.Exists(physicalPath))
            return Results.NotFound("File not found");

        var extension = Path.GetExtension(physicalPath).ToLowerInvariant();
        var type = GetAssetType(extension);

        // Parse size
        if (!Enum.TryParse<ThumbnailSize>(size, true, out var thumbnailSize))
        {
            thumbnailSize = ThumbnailSize.Medium;
        }

        try
        {
            byte[] thumbnailBytes;
            string contentType;

            if (type == AssetType.Image)
            {
                // Generar miniatura de imagen
                var targetSize = thumbnailSize switch
                {
                    ThumbnailSize.Small => 220,
                    ThumbnailSize.Medium => 640,
                    ThumbnailSize.Large => 1280,
                    _ => 640
                };

                using var image = await Image.LoadAsync(physicalPath, cancellationToken);
                
                // Aplicar orientación EXIF si existe
                var orientation = GetImageOrientation(image);
                if (orientation != 0)
                {
                    image.Mutate(x => x.AutoOrient());
                }

                // Calcular dimensiones manteniendo aspect ratio
                var (width, height) = CalculateThumbnailSize(image.Width, image.Height, targetSize);
                
                image.Mutate(x => x.Resize(new ResizeOptions
                {
                    Size = new Size(width, height),
                    Mode = ResizeMode.Max
                }));

                // Convertir a JPEG
                using var ms = new MemoryStream();
                await image.SaveAsync(ms, new JpegEncoder { Quality = 85 }, cancellationToken);
                thumbnailBytes = ms.ToArray();
                contentType = "image/jpeg";
            }
            else
            {
                // Para videos, usar el primer frame (simplificado - en producción usar FFmpeg)
                return Results.BadRequest("Video thumbnails for pending assets require FFmpeg and are not yet implemented");
            }

            var fileName = Path.GetFileName(physicalPath);
            return Results.File(thumbnailBytes, contentType, $"{fileName}_thumb_{size}.jpg");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] Error generating thumbnail for pending asset {path}: {ex.Message}");
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private int GetImageOrientation(Image image)
    {
        // Intentar leer la orientación EXIF
        try
        {
            if (image.Metadata.ExifProfile != null)
            {
                var orientationTag = image.Metadata.ExifProfile.Values
                    .FirstOrDefault(v => v.Tag == SixLabors.ImageSharp.Metadata.Profiles.Exif.ExifTag.Orientation);
                if (orientationTag != null && orientationTag.GetValue() is ushort orientationValue)
                {
                    return orientationValue;
                }
            }
        }
        catch
        {
            // Ignorar errores al leer EXIF
        }
        return 0;
    }

    private (int width, int height) CalculateThumbnailSize(int originalWidth, int originalHeight, int targetSize)
    {
        if (originalWidth <= targetSize && originalHeight <= targetSize)
        {
            return (originalWidth, originalHeight);
        }

        var ratio = Math.Min((double)targetSize / originalWidth, (double)targetSize / originalHeight);
        return ((int)(originalWidth * ratio), (int)(originalHeight * ratio));
    }

    private bool IsPathSafe(string path, string assetsPath)
    {
        var fullPath = Path.GetFullPath(path);
        var fullAssetsPath = Path.GetFullPath(assetsPath);
        return fullPath.StartsWith(fullAssetsPath, StringComparison.OrdinalIgnoreCase);
    }

    private AssetType GetAssetType(string extension)
    {
        var imageExtensions = new[] { ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".gif", ".webp", ".heic", ".heif" };
        return imageExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase) ? AssetType.Image : AssetType.Video;
    }

    private string GetContentType(string extension, AssetType type)
    {
        return extension switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".webp" => "image/webp",
            ".gif" => "image/gif",
            ".mp4" => "video/mp4",
            ".mov" => "video/quicktime",
            ".avi" => "video/x-msvideo",
            ".mkv" => "video/x-matroska",
            _ => type == AssetType.Video ? "video/mp4" : "application/octet-stream"
        };
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(userIdClaim?.Value, out userId);
    }
}
