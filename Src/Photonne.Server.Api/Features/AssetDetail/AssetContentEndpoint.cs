using ImageMagick;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetDetail;

public class AssetContentEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId}/content", Handle)
            .WithName("GetAssetContent")
            .WithTags("Assets")
            .WithDescription("Gets the content of an asset (image or video). With download=true, " +
                             "format=original|jpeg chooses what a RAW or HEIC/HEIF is downloaded as");
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromServices] ILogger<AssetContentEndpoint> logger,
        [FromRoute] Guid assetId,
        [FromQuery] bool? download,
        [FromQuery] string? format,
        CancellationToken cancellationToken)
    {
        if (!AssetDownloadFormats.TryParse(format, out var downloadFormat))
            return Results.BadRequest(new { error = "format must be 'original' or 'jpeg'" });

        var asset = await dbContext.Assets.FindAsync(new object[] { assetId }, cancellationToken);

        if (asset == null)
        {
            return Results.NotFound(new { error = $"Asset with ID {assetId} not found" });
        }

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);

        if (!File.Exists(physicalPath))
        {
            logger.LogWarning("Asset {AssetId}: file not found at resolved path '{PhysicalPath}' (DB path: '{DbPath}')",
                assetId, physicalPath, asset.FullPath);
            return Results.NotFound(new { error = $"File not found at: {physicalPath}" });
        }

        var extension = Path.GetExtension(physicalPath).ToLowerInvariant();

        // Most browsers and image views can't paint a HEIC, and none paints a
        // RAW: to look at one, serve a JPEG. A download is whatever was asked
        // for — see AssetDownloadFormats for what a request that doesn't say gets.
        var asJpeg = download == true
            ? AssetDownloadFormats.ConvertsOnDownload(extension, downloadFormat)
            : AssetDownloadFormats.IsConvertible(extension);

        if (asJpeg)
        {
            try
            {
                var jpegBytes = RawImageLoader.RenderJpeg(physicalPath);
                return download == true
                    ? Results.File(jpegBytes, "image/jpeg",
                        fileDownloadName: AssetDownloadFormats.JpegFileName(asset.FileName))
                    : Results.File(jpegBytes, "image/jpeg");
            }
            catch (MagickException ex)
            {
                logger.LogWarning("Asset {AssetId}: could not be converted to JPEG, serving the original ({Reason})",
                    assetId, ex.Message);
            }
        }

        var contentType = GetContentType(extension, asset.Type);

        if (download == true)
            return Results.File(physicalPath, contentType, fileDownloadName: asset.FileName);

        return Results.File(physicalPath, contentType, enableRangeProcessing: true);
    }

    private string GetContentType(string extension, AssetType type)
    {
        return extension switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".webp" => "image/webp",
            ".gif" => "image/gif",
            ".heic" => "image/heic",
            ".heif" => "image/heif",
            ".dng" => "image/x-adobe-dng",
            ".mp4" => "video/mp4",
            ".mov" => "video/quicktime",
            ".avi" => "video/x-msvideo",
            ".mkv" => "video/x-matroska",
            _ => type == AssetType.Video ? "video/mp4" : "application/octet-stream"
        };
    }
}
