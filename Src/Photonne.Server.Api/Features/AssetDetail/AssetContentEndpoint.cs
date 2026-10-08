using ImageMagick;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Extensions;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetDetail;

public class AssetContentEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId}/content", Handle)
            .RequireAuthorization()
            .Produces<Stream>(StatusCodes.Status200OK, "image/jpeg", "image/png", "image/webp", "image/gif",
                "image/heic", "image/heif", "image/x-adobe-dng", "video/mp4", "video/quicktime",
                "video/x-msvideo", "video/x-matroska", "application/octet-stream")
            .Produces(StatusCodes.Status304NotModified)
            .WithName("GetAssetContent")
            .WithTags("Assets")
            .WithDescription("Gets the content of an asset (image or video). With download=true, " +
                             "format=original|jpeg chooses what a RAW or HEIC/HEIF is downloaded as");
    }

    private async Task<Results<FileContentHttpResult, PhysicalFileHttpResult, StatusCodeHttpResult, BadRequest<ApiError>, NotFound<ApiError>>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromServices] ILogger<AssetContentEndpoint> logger,
        [FromServices] AssetVisibilityService visibility,
        HttpContext httpContext,
        [FromRoute] Guid assetId,
        [FromQuery] bool? download,
        [FromQuery] string? format,
        CancellationToken cancellationToken)
    {
        if (!AssetDownloadFormats.TryParse(format, out var downloadFormat))
            return TypedResults.BadRequest(new ApiError("format must be 'original' or 'jpeg'", "invalid_format"));

        var asset = await dbContext.Assets.FindAsync(new object[] { assetId }, cancellationToken);

        if (asset == null || !await visibility.CanReadAsync(httpContext.User, asset, cancellationToken))
        {
            return TypedResults.NotFound(new ApiError($"Asset with ID {assetId} not found", "asset_not_found"));
        }

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);

        if (!File.Exists(physicalPath))
        {
            logger.LogWarning("Asset {AssetId}: file not found at resolved path '{PhysicalPath}' (DB path: '{DbPath}')",
                assetId, physicalPath, asset.FullPath);
            return TypedResults.NotFound(new ApiError($"File of asset {assetId} not found", "file_not_found"));
        }

        var extension = Path.GetExtension(physicalPath).ToLowerInvariant();

        // Most browsers and image views can't paint a HEIC, and none paints a
        // RAW: to look at one, serve a JPEG. A download is whatever was asked
        // for — see AssetDownloadFormats for what a request that doesn't say gets.
        var asJpeg = download == true
            ? AssetDownloadFormats.ConvertsOnDownload(extension, downloadFormat)
            : AssetDownloadFormats.IsConvertible(extension);

        var file = new FileInfo(physicalPath);
        MediaCaching.ApplyCacheControl(httpContext);

        if (asJpeg)
        {
            // The render is the expensive part: answer a revalidation before it.
            var jpegTag = MediaCaching.ETagFor(file, "jpeg");
            if (MediaCaching.IsNotModified(httpContext.Request, jpegTag))
            {
                // Same as MediaCaching.NotModified, as a typed result.
                httpContext.Response.Headers.ETag = jpegTag.ToString();
                return TypedResults.StatusCode(StatusCodes.Status304NotModified);
            }

            try
            {
                var jpegBytes = RawImageLoader.RenderJpeg(physicalPath);
                return download == true
                    ? TypedResults.File(jpegBytes, "image/jpeg",
                        fileDownloadName: AssetDownloadFormats.JpegFileName(asset.FileName),
                        entityTag: jpegTag)
                    : TypedResults.File(jpegBytes, "image/jpeg", entityTag: jpegTag);
            }
            catch (MagickException ex)
            {
                logger.LogWarning("Asset {AssetId}: could not be converted to JPEG, serving the original ({Reason})",
                    assetId, ex.Message);
            }
        }

        var contentType = GetContentType(extension, asset.Type);

        var etag = MediaCaching.ETagFor(file);

        if (download == true)
            return TypedResults.PhysicalFile(physicalPath, contentType, fileDownloadName: asset.FileName,
                lastModified: file.LastWriteTimeUtc, entityTag: etag);

        return TypedResults.PhysicalFile(physicalPath, contentType,
            lastModified: file.LastWriteTimeUtc, entityTag: etag, enableRangeProcessing: true);
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
