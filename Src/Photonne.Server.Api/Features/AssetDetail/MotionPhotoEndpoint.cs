using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Extensions;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetDetail;

/// <summary>
/// Serves the motion clip paired with an iOS Live Photo: the sibling
/// <c>{basename}.mov</c> that sits next to the still in the same directory.
/// The pairing isn't persisted anywhere (only the <see cref="AssetTagType.LivePhoto"/>
/// tag is), so we resolve the sibling on disk at request time — the same
/// approach <see cref="Services.MediaRecognitionService"/> uses to detect it.
///
/// Streamed with range processing so the client's looping motion player can
/// seek/scrub cheaply. Returns 404 when the asset has no paired clip, which is
/// the signal the client uses to fall back to a plain still.
///
/// Samsung/Google "Motion Photos" embed the MP4 inside the JPEG rather than as a
/// sibling file; when there's no sibling clip we fall back to extracting the
/// embedded video (<see cref="EmbeddedMotionPhotoExtractor"/>) and stream that.
/// </summary>
public class MotionPhotoEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/motion", Handle)
            .RequireAuthorization()
            .WithName("GetAssetMotion")
            .WithTags("Assets")
            .WithDescription("Gets the paired motion video for a Live Photo, if one exists");
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromServices] AssetVisibilityService visibility,
        HttpContext httpContext,
        [FromRoute] Guid assetId,
        CancellationToken cancellationToken)
    {
        var asset = await dbContext.Assets
            .AsNoTracking()
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken);

        if (asset == null || !await visibility.CanReadAsync(httpContext.User, asset, cancellationToken))
        {
            return Results.NotFound(new { error = $"Asset with ID {assetId} not found" });
        }

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
        var motionPath = MotionFrameService.ResolveSiblingClipPath(physicalPath);

        MediaCaching.ApplyCacheControl(httpContext);

        if (motionPath != null)
        {
            var clipFile = new FileInfo(motionPath);
            return Results.File(motionPath, "video/quicktime",
                lastModified: clipFile.LastWriteTimeUtc,
                entityTag: MediaCaching.ETagFor(clipFile),
                enableRangeProcessing: true);
        }

        // No sibling clip — try a Samsung/Google motion photo with the MP4
        // embedded inside the still. The clip is the trailing bytes from the
        // resolved offset to EOF; copy that slice into a seekable stream so
        // range processing works the same as the sibling path.
        var embedded = EmbeddedMotionPhotoExtractor.ResolveEmbeddedVideo(physicalPath);
        if (embedded is { } range)
        {
            var motionTag = MediaCaching.ETagFor(new FileInfo(physicalPath), "motion");
            if (MediaCaching.IsNotModified(httpContext.Request, motionTag))
                return MediaCaching.NotModified(httpContext, motionTag);

            var clip = new MemoryStream();
            await using (var file = File.OpenRead(physicalPath))
            {
                file.Position = range.Offset;
                await file.CopyToAsync(clip, cancellationToken);
            }
            clip.Position = 0;
            return Results.Stream(clip, "video/mp4", entityTag: motionTag, enableRangeProcessing: true);
        }

        return Results.NotFound(new { error = $"Asset {assetId} has no paired motion clip" });
    }
}
