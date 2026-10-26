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

/// <summary>
/// The video to play: its playback copy (H.264 MP4, see
/// <see cref="Shared.Services.Video.VideoPlaybackService"/>) when it has one,
/// the original otherwise — so the clients always point their player here and
/// keep <c>/content</c> for downloads. Byte ranges work either way, for seeking.
/// </summary>
public class AssetPlaybackEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId}/playback", Handle)
            .RequireAuthorization()
            .Produces<Stream>(StatusCodes.Status200OK, "video/mp4", "video/quicktime", "video/x-msvideo",
                "video/x-matroska", "application/octet-stream")
            .Produces(StatusCodes.Status304NotModified)
            .WithName("GetAssetPlayback")
            .WithTags("Assets")
            .WithDescription("Gets the video to play: its H.264 playback copy when it has one, else the original");
    }

    private static async Task<Results<PhysicalFileHttpResult, StatusCodeHttpResult, NotFound<ApiError>>> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] SettingsService settings,
        [FromServices] AssetVisibilityService visibility,
        HttpContext httpContext,
        [FromRoute] Guid assetId,
        CancellationToken ct)
    {
        var asset = await db.Assets.FindAsync([assetId], ct);
        if (asset == null || !await visibility.CanReadAsync(httpContext.User, asset, ct))
            return TypedResults.NotFound(new ApiError($"Asset with ID {assetId} not found", "asset_not_found"));

        MediaCaching.ApplyCacheControl(httpContext);

        var playback = await db.AssetPlaybacks.FindAsync([assetId], ct);
        if (playback is { Status: PlaybackStatus.Ready, FilePath: { } copy } && File.Exists(copy))
        {
            var copyFile = new FileInfo(copy);
            return TypedResults.PhysicalFile(copy, "video/mp4", lastModified: copyFile.LastWriteTimeUtc,
                entityTag: MediaCaching.ETagFor(copyFile, "playback"), enableRangeProcessing: true);
        }

        var original = await settings.ResolvePhysicalPathAsync(asset.FullPath);
        if (!File.Exists(original))
            return TypedResults.NotFound(new ApiError($"File of asset {assetId} not found", "file_not_found"));
        var file = new FileInfo(original);
        return TypedResults.PhysicalFile(original,
            AssetContentEndpoint.GetContentType(file.Extension.ToLowerInvariant(), asset.Type),
            lastModified: file.LastWriteTimeUtc, entityTag: MediaCaching.ETagFor(file), enableRangeProcessing: true);
    }
}
