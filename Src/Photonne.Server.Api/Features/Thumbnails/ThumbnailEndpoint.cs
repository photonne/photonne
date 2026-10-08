using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Extensions;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Scalar.AspNetCore;

namespace Photonne.Server.Api.Features.Thumbnails;

public class ThumbnailEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/thumbnail", Handle)
            .CodeSample(
                codeSample: "curl -X GET \"http://localhost:5000/api/assets/1/thumbnail?size=Medium\" -o thumbnail.jpg",
                label: "cURL Example")
            .RequireAuthorization()
            .Produces<Stream>(StatusCodes.Status200OK, "image/jpeg", "image/webp")
            .WithName("GetThumbnail")
            .WithTags("Assets")
            .WithDescription("Gets a thumbnail for an asset")
            .AddOpenApiOperationTransformer((operation, context, ct) =>
            {
                operation.Summary = "Get asset thumbnail";
                operation.Description = "Returns a thumbnail image file for the specified asset. Supports Small (220px), Medium (640px), and Large (1280px) sizes.";
                return Task.CompletedTask;
            });
    }

    private async Task<Results<PhysicalFileHttpResult, NotFound<ApiError>>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] ThumbnailGeneratorService thumbnailService,
        [FromServices] SettingsService settingsService,
        [FromServices] AssetVisibilityService visibility,
        HttpContext httpContext,
        [FromRoute] Guid assetId,
        [FromQuery] string size = "Medium",
        CancellationToken cancellationToken = default)
    {
        // Validate asset exists
        var asset = await dbContext.Assets
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken);

        if (asset == null || !await visibility.CanReadAsync(httpContext.User, asset, cancellationToken))
        {
            return TypedResults.NotFound(new ApiError($"Asset with ID {assetId} not found", "asset_not_found"));
        }

        // Parse size
        if (!Enum.TryParse<ThumbnailSize>(size, true, out var thumbnailSize))
        {
            thumbnailSize = ThumbnailSize.Medium;
        }

        // Get thumbnail from database
        var thumbnail = await dbContext.AssetThumbnails
            .FirstOrDefaultAsync(t => t.AssetId == assetId && t.Size == thumbnailSize, cancellationToken);

        // If thumbnail doesn't exist in DB or file doesn't exist, generate it on-demand
        if (thumbnail == null || !File.Exists(thumbnail.FilePath))
        {
            Console.WriteLine($"[THUMBNAIL] Generating thumbnail on-demand for asset {assetId}, size {size}");
            
            // Resolve physical path of the asset
            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            
            if (!File.Exists(physicalPath))
            {
                return TypedResults.NotFound(new ApiError($"File of asset {assetId} not found", "file_not_found"));
            }

            // Generate thumbnails for all sizes (to ensure we have them all)
            List<AssetThumbnail> generatedThumbnails;
            try
            {
                generatedThumbnails = await thumbnailService.GenerateThumbnailsAsync(physicalPath, assetId, cancellationToken);
            }
            catch (ThumbnailGenerationException ex)
            {
                Console.WriteLine($"[THUMBNAILS] {ex.Message}");
                return TypedResults.NotFound(new ApiError($"Failed to generate thumbnail for asset {assetId}", "thumbnail_generation_failed"));
            }

            if (generatedThumbnails.Any())
            {
                // GenerateThumbnailsAsync always emits Small/Medium/Large; some of those
                // sizes may already have a row for this asset (unique on AssetId+Size),
                // so only persist the genuinely new ones to avoid 23505.
                var existingSizes = await dbContext.AssetThumbnails
                    .Where(t => t.AssetId == assetId)
                    .Select(t => t.Size)
                    .ToListAsync(cancellationToken);

                var newRows = generatedThumbnails
                    .Where(t => !existingSizes.Contains(t.Size))
                    .ToList();

                if (newRows.Any())
                {
                    dbContext.AssetThumbnails.AddRange(newRows);
                    await dbContext.SaveChangesAsync(cancellationToken);
                }

                // Disk file for the requested size was just (re)written by the generator.
                thumbnail = generatedThumbnails.FirstOrDefault(t => t.Size == thumbnailSize);
            }
            
            if (thumbnail == null)
            {
                return TypedResults.NotFound(new ApiError($"Failed to generate thumbnail for asset {assetId} with size {size}", "thumbnail_generation_failed"));
            }
        }

        // Check if file exists
        if (!File.Exists(thumbnail.FilePath))
        {
            return TypedResults.NotFound(new ApiError($"Thumbnail of asset {assetId} not found", "thumbnail_not_found"));
        }

        var contentType = thumbnail.Format == "WebP" ? "image/webp" : "image/jpeg";
        var file = new FileInfo(thumbnail.FilePath);

        MediaCaching.ApplyCacheControl(httpContext);
        return TypedResults.PhysicalFile(thumbnail.FilePath, contentType,
            lastModified: file.LastWriteTimeUtc,
            entityTag: MediaCaching.ETagFor(file));
    }
}
