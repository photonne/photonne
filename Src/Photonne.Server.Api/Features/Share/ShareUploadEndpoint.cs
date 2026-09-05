using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Features.UploadAssets;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Share;

/// <summary>
/// Public upload endpoint for photo-request links ("solicitud de fotos") — no
/// auth required. Visitors without an account (e.g. wedding guests) upload
/// photos through a share link whose <c>AllowUpload</c> flag is on. The asset
/// lands under the link creator's <c>PhotoRequests/{album}[/{invitado}]</c>
/// folder, owned by the creator, and is added to the shared album.
/// </summary>
public class ShareUploadEndpoint : IEndpoint
{
    private const string PhotoRequestsFolder = "PhotoRequests";

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/share/{token}/upload", Handle)
            .DisableAntiforgery()
            .WithName("ShareUpload")
            .WithTags("Share")
            .WithDescription("Uploads a photo to a shared album through a photo-request link (no authentication required)")
            .RequireRateLimiting("share-upload");
    }

    private static async Task<IResult> Handle(
        [FromRoute] string token,
        [FromForm] IFormFile file,
        [FromForm] string? uploaderName,
        [FromForm] string? pw,
        [FromForm] string? fileModifiedAt,
        [FromForm] string? fileCreatedAt,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] FileHashService hashService,
        [FromServices] IEnrichmentService enrichmentService,
        [FromServices] SettingsService settingsService,
        [FromServices] INotificationService notificationService,
        [FromServices] IMemoryCache cache,
        [FromServices] ILogger<ShareUploadEndpoint> logger,
        CancellationToken cancellationToken)
    {
        if (file == null || file.Length == 0)
            return Results.BadRequest(new { error = "No file uploaded" });

        var link = await dbContext.SharedLinks
            .Include(l => l.Album)
            .Include(l => l.CreatedBy)
            .FirstOrDefaultAsync(l => l.Token == token && l.AlbumId != null, cancellationToken);

        if (link == null) return Results.NotFound(new { error = "Share link not found" });

        if (link.ExpiresAt.HasValue && link.ExpiresAt.Value < DateTime.UtcNow)
            return Results.Json(new { error = "This link has expired" }, statusCode: 410);

        if (link.MaxViews.HasValue && link.ViewCount >= link.MaxViews.Value)
            return Results.Json(new { error = "This link has reached its maximum number of views" }, statusCode: 410);

        if (link.PasswordHash != null && !SharePasswordHasher.Verify(pw ?? string.Empty, link.PasswordHash))
            return Results.Json(new { error = "Invalid password" }, statusCode: 401);

        if (!link.AllowUpload) return Results.Forbid();

        var owner = link.CreatedBy;
        if (owner == null || string.IsNullOrEmpty(owner.Username))
            return Results.Problem("Link owner not found");

        // Mismos límites que la subida autenticada, cargados contra el DUEÑO del
        // enlace: las fotos de los invitados cuentan en su cuota, no en la de nadie.
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

        if (owner.StorageQuotaBytes.HasValue)
        {
            var usedBytes = await dbContext.Assets
                .Where(a => a.OwnerId == owner.Id && a.DeletedAt == null)
                .SumAsync(a => (long?)a.FileSize, cancellationToken) ?? 0L;

            if (usedBytes + file.Length > owner.StorageQuotaBytes.Value)
                return Results.Problem(
                    detail: "El propietario del álbum ha alcanzado su límite de almacenamiento.",
                    statusCode: StatusCodes.Status409Conflict);
        }

        // Destino: PhotoRequests/{álbum} con subcarpeta opcional por invitado, para
        // saber quién subió qué sin necesidad de cuentas. El nombre del álbum puede
        // sanitizarse a vacío (p. ej. solo emoji); el token siempre es un segmento válido.
        var albumSegment = DeviceFolderSanitizer.Sanitize(link.Album!.Name) ?? link.Token;
        var uploaderSegment = DeviceFolderSanitizer.Sanitize(uploaderName);
        var virtualPath = $"/assets/users/{owner.Username}/{PhotoRequestsFolder}/{albumSegment}";
        if (uploaderSegment != null) virtualPath += $"/{uploaderSegment}";

        var physicalRoot = await settingsService.ResolvePhysicalPathAsync(virtualPath);
        var folder = await UploadAssetsEndpoint.EnsureFolderRecordAsync(dbContext, owner.Id, virtualPath, cancellationToken);

        if (!Directory.Exists(physicalRoot))
            Directory.CreateDirectory(physicalRoot);

        var tempPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + Path.GetExtension(file.FileName));
        try
        {
            using (var stream = new FileStream(tempPath, FileMode.Create))
            {
                await file.CopyToAsync(stream, cancellationToken);
            }

            var checksum = await hashService.CalculateFileHashAsync(tempPath, cancellationToken);

            var existingAsset = await dbContext.Assets
                .FirstOrDefaultAsync(a => a.Checksum == checksum, cancellationToken);
            if (existingAsset != null)
            {
                File.Delete(tempPath);

                // Duplicado exacto. Si el asset ya es del dueño del enlace, lo
                // enlazamos igualmente al álbum para que el invitado lo vea; si
                // pertenece a OTRO usuario no lo tocamos (enlazarlo expondría
                // contenido ajeno en una página pública). Sin assetId en la
                // respuesta: un visitante anónimo no necesita ids internos.
                if (existingAsset.OwnerId == owner.Id && existingAsset.DeletedAt == null)
                    await AddToAlbumAsync(dbContext, cache, link, existingAsset.Id, owner.Id, cancellationToken);

                return Results.Ok(new { message = "Asset already exists" });
            }

            var finalFileName = file.FileName;
            var targetPath = Path.Combine(physicalRoot, finalFileName);
            if (File.Exists(targetPath))
            {
                finalFileName = $"{Guid.NewGuid()}_{file.FileName}";
                targetPath = Path.Combine(physicalRoot, finalFileName);
            }

            File.Move(tempPath, targetPath);

            var modifiedUtc = UploadAssetsEndpoint.ParseClientTimestamp(fileModifiedAt);
            var createdUtc = UploadAssetsEndpoint.ParseClientTimestamp(fileCreatedAt);
            if (modifiedUtc.HasValue) File.SetLastWriteTimeUtc(targetPath, modifiedUtc.Value);
            if (createdUtc.HasValue) File.SetCreationTimeUtc(targetPath, createdUtc.Value);

            var fileInfo = new FileInfo(targetPath);
            var extension = Path.GetExtension(targetPath).ToLowerInvariant();
            var assetType = UploadAssetsEndpoint.GetAssetType(extension);
            var dbPath = await settingsService.VirtualizePathAsync(targetPath);
            var seedTz = await MetadataTimeZone.ResolveAsync(settingsService, cancellationToken);

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
                CapturedAt = MetadataTimeZone.ToLocalWallClock(
                    createdUtc ?? modifiedUtc ?? fileInfo.CreationTimeUtc, seedTz),
                ScannedAt = DateTime.UtcNow,
                FolderId = folder?.Id,
                OwnerId = owner.Id
            };

            dbContext.Assets.Add(asset);
            await dbContext.SaveChangesAsync(cancellationToken);

            await AddToAlbumAsync(dbContext, cache, link, asset.Id, owner.Id, cancellationToken);

            if (assetType == AssetType.Image || assetType == AssetType.Video)
            {
                await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Exif, cancellationToken);
                await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.MediaRecognition, cancellationToken);
            }
            await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails, cancellationToken);

            // Aviso al dueño en hitos (como las visitas) para que una tanda de 80
            // fotos de boda no genere 80 notificaciones.
            if (ShouldNotifyUpload(link.UploadCount))
            {
                var albumName = link.Album.Name;
                var photoText = link.UploadCount == 1 ? "1 foto" : $"{link.UploadCount} fotos";
                await notificationService.CreateAsync(
                    link.CreatedById,
                    NotificationType.ShareUploaded,
                    "Fotos de invitados",
                    $"\"{albumName}\" ha recibido {photoText} a través del enlace de solicitud.",
                    $"/albums/{link.AlbumId}");
            }

            return Results.Ok(new { message = "Asset uploaded successfully" });
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Guest upload failed for share {Token}, file {FileName}", token, file.FileName);
            if (File.Exists(tempPath)) File.Delete(tempPath);
            return Results.Problem("Upload failed");
        }
    }

    /// <summary>
    /// Adds the asset to the link's album (idempotent), bumps the link's upload
    /// counter, refreshes the album cover/UpdatedAt and invalidates the owner's
    /// album cache — mirrors AlbumsEndpoint.AddAssetToAlbum.
    /// </summary>
    private static async Task AddToAlbumAsync(
        ApplicationDbContext dbContext,
        IMemoryCache cache,
        SharedLink link,
        Guid assetId,
        Guid ownerId,
        CancellationToken ct)
    {
        var albumId = link.AlbumId!.Value;

        var alreadyInAlbum = await dbContext.AlbumAssets
            .AnyAsync(aa => aa.AlbumId == albumId && aa.AssetId == assetId, ct);
        if (alreadyInAlbum) return;

        var maxOrder = 0;
        if (await dbContext.AlbumAssets.AnyAsync(aa => aa.AlbumId == albumId, ct))
        {
            maxOrder = await dbContext.AlbumAssets
                .Where(aa => aa.AlbumId == albumId)
                .MaxAsync(aa => aa.Order, ct);
        }

        dbContext.AlbumAssets.Add(new AlbumAsset
        {
            AlbumId = albumId,
            AssetId = assetId,
            Order = maxOrder + 1,
            AddedAt = DateTime.UtcNow
        });

        if (link.Album!.CoverAssetId == null)
            link.Album.CoverAssetId = assetId;

        link.Album.UpdatedAt = DateTime.UtcNow;
        link.UploadCount++;

        await dbContext.SaveChangesAsync(ct);
        cache.Remove($"albums:{ownerId}");
    }

    private static bool ShouldNotifyUpload(int uploadCount)
        => uploadCount is 1 or 5 or 10 or 25 or 50 or 100
           || (uploadCount > 100 && uploadCount % 100 == 0);
}
