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
            .WithDescription("Gets the original content of an asset (image or video)");
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromRoute] Guid assetId,
        [FromQuery] bool? download,
        CancellationToken cancellationToken)
    {
        var asset = await dbContext.Assets.FindAsync(new object[] { assetId }, cancellationToken);

        if (asset == null)
        {
            return Results.NotFound(new { error = $"Asset with ID {assetId} not found" });
        }

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);

        if (!File.Exists(physicalPath))
        {
            return Results.NotFound(new { error = $"File not found at: {physicalPath}" });
        }

        var extension = Path.GetExtension(physicalPath).ToLowerInvariant();
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
            ".mp4" => "video/mp4",
            ".mov" => "video/quicktime",
            ".avi" => "video/x-msvideo",
            ".mkv" => "video/x-matroska",
            _ => type == AssetType.VIDEO ? "video/mp4" : "application/octet-stream"
        };
    }
}
