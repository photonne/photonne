using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
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

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] ThumbnailGeneratorService thumbnailService,
        [FromServices] SettingsService settingsService,
        HttpContext httpContext,
        [FromRoute] Guid assetId,
        [FromQuery] string size = "Medium",
        CancellationToken cancellationToken = default)
    {
        try
        {
            // Validate asset exists
            var asset = await dbContext.Assets
                .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken);

            if (asset == null)
            {
                return Results.NotFound(new { error = $"Asset with ID {assetId} not found" });
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
                    return Results.NotFound(new { error = $"Asset file not found at path: {physicalPath}" });
                }

                // Generate thumbnails for all sizes (to ensure we have them all)
                var generatedThumbnails = await thumbnailService.GenerateThumbnailsAsync(physicalPath, assetId, cancellationToken);
                
                if (generatedThumbnails.Any())
                {
                    // Save to database
                    dbContext.AssetThumbnails.AddRange(generatedThumbnails);
                    await dbContext.SaveChangesAsync(cancellationToken);
                    
                    // Find the requested size
                    thumbnail = generatedThumbnails.FirstOrDefault(t => t.Size == thumbnailSize);
                }
                
                if (thumbnail == null)
                {
                    return Results.NotFound(new { error = $"Failed to generate thumbnail for asset {assetId} with size {size}" });
                }
            }

            // Check if file exists
            if (!File.Exists(thumbnail.FilePath))
            {
                return Results.NotFound(new { error = $"Thumbnail file not found at path: {thumbnail.FilePath}" });
            }

            // Return file
            var fileBytes = await File.ReadAllBytesAsync(thumbnail.FilePath, cancellationToken);
            var contentType = thumbnail.Format == "WebP" ? "image/webp" : "image/jpeg";

            // Thumbnails are immutable (keyed by assetId + size) — cache aggressively
            httpContext.Response.Headers.CacheControl = "public, max-age=2592000, immutable";

            return Results.File(fileBytes, contentType, $"{asset.FileName}_thumb_{size}.jpg");
        }
        catch (Exception ex)
        {
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }
}
