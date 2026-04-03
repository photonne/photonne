using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using static Photonne.Server.Api.Shared.Services.SharePasswordHasher;

namespace Photonne.Server.Api.Features.Share;

/// <summary>
/// Public media endpoints for shared links — no auth required.
/// Serves thumbnail and content for both asset and album shares.
/// </summary>
public class ShareMediaEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/share/{token}/asset/{assetId:guid}/thumbnail", HandleAlbumAssetThumbnail).WithTags("Share");
        app.MapGet("/api/share/{token}/asset/{assetId:guid}/content", HandleAlbumAssetContent).WithTags("Share");
    }

    // ── Album asset ───────────────────────────────────────────────────────────

    private static async Task<IResult> HandleAlbumAssetThumbnail(
        [FromServices] ApplicationDbContext db,
        [FromServices] ThumbnailGeneratorService thumbnailService,
        [FromServices] SettingsService settings,
        [FromRoute] string token,
        [FromRoute] Guid assetId,
        [FromQuery] string size = "Medium",
        [FromQuery] string? pw = null,
        CancellationToken ct = default)
    {
        var link = await db.SharedLinks
            .Include(l => l.Album).ThenInclude(a => a!.AlbumAssets)
            .FirstOrDefaultAsync(l => l.Token == token && l.AlbumId != null, ct);

        if (!IsValidLink(link, pw)) return Results.NotFound();
        if (link!.Album!.AlbumAssets.All(aa => aa.AssetId != assetId)) return Results.Forbid();

        var asset = await db.Assets.Include(a => a.Thumbnails).FirstOrDefaultAsync(a => a.Id == assetId, ct);
        if (asset == null) return Results.NotFound();

        return await ServeThumbnail(db, thumbnailService, settings, asset, size, ct);
    }

    private static async Task<IResult> HandleAlbumAssetContent(
        [FromServices] ApplicationDbContext db,
        [FromServices] SettingsService settings,
        [FromRoute] string token,
        [FromRoute] Guid assetId,
        [FromQuery] bool? download,
        [FromQuery] string? pw = null,
        CancellationToken ct = default)
    {
        var link = await db.SharedLinks
            .Include(l => l.Album).ThenInclude(a => a!.AlbumAssets)
            .FirstOrDefaultAsync(l => l.Token == token && l.AlbumId != null, ct);

        if (!IsValidLink(link, pw)) return Results.NotFound();
        if (link!.Album!.AlbumAssets.All(aa => aa.AssetId != assetId)) return Results.Forbid();
        if (download == true && !link.AllowDownload) return Results.Forbid();

        var asset = await db.Assets.FirstOrDefaultAsync(a => a.Id == assetId, ct);
        if (asset == null) return Results.NotFound();

        return await ServeContent(settings, asset, download, ct);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static bool IsValidLink(Shared.Models.SharedLink? link, string? pw)
    {
        if (link == null) return false;
        if (link.ExpiresAt.HasValue && link.ExpiresAt.Value < DateTime.UtcNow) return false;
        if (link.MaxViews.HasValue && link.ViewCount > link.MaxViews.Value) return false;
        if (link.PasswordHash != null && !Verify(pw ?? string.Empty, link.PasswordHash)) return false;
        return true;
    }

    private static async Task<IResult> ServeThumbnail(
        ApplicationDbContext db,
        ThumbnailGeneratorService thumbnailService,
        SettingsService settings,
        Asset asset,
        string size,
        CancellationToken ct)
    {
        if (!Enum.TryParse<ThumbnailSize>(size, true, out var thumbnailSize))
            thumbnailSize = ThumbnailSize.Medium;

        var thumbnail = await db.AssetThumbnails
            .FirstOrDefaultAsync(t => t.AssetId == asset.Id && t.Size == thumbnailSize, ct);

        if (thumbnail == null || !File.Exists(thumbnail.FilePath))
        {
            var physicalPath = await settings.ResolvePhysicalPathAsync(asset.FullPath);
            if (!File.Exists(physicalPath)) return Results.NotFound();

            var generated = await thumbnailService.GenerateThumbnailsAsync(physicalPath, asset.Id, ct);
            if (generated.Any())
            {
                db.AssetThumbnails.AddRange(generated);
                await db.SaveChangesAsync(ct);
                thumbnail = generated.FirstOrDefault(t => t.Size == thumbnailSize);
            }
        }

        if (thumbnail == null || !File.Exists(thumbnail.FilePath)) return Results.NotFound();

        var bytes = await File.ReadAllBytesAsync(thumbnail.FilePath, ct);
        var contentType = thumbnail.Format == "WebP" ? "image/webp" : "image/jpeg";
        return Results.File(bytes, contentType);
    }

    private static async Task<IResult> ServeContent(
        SettingsService settings,
        Asset asset,
        bool? download,
        CancellationToken ct)
    {
        var physicalPath = await settings.ResolvePhysicalPathAsync(asset.FullPath);
        if (!File.Exists(physicalPath)) return Results.NotFound();

        var ext = Path.GetExtension(physicalPath).ToLowerInvariant();
        var contentType = ext switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".webp" => "image/webp",
            ".gif" => "image/gif",
            ".mp4" => "video/mp4",
            ".mov" => "video/quicktime",
            ".avi" => "video/x-msvideo",
            ".mkv" => "video/x-matroska",
            _ => asset.Type == AssetType.VIDEO ? "video/mp4" : "application/octet-stream"
        };

        if (download == true)
            return Results.File(physicalPath, contentType, fileDownloadName: asset.FileName);

        return Results.File(physicalPath, contentType, enableRangeProcessing: true);
    }
}
