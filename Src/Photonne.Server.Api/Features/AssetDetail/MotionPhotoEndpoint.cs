using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
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
/// Out of scope: Android "Motion Photos" embed the video inside the JPEG
/// rather than as a sibling file; those aren't served here.
/// </summary>
public class MotionPhotoEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/motion", Handle)
            .WithName("GetAssetMotion")
            .WithTags("Assets")
            .WithDescription("Gets the paired motion video for a Live Photo, if one exists");
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromRoute] Guid assetId,
        CancellationToken cancellationToken)
    {
        var asset = await dbContext.Assets
            .AsNoTracking()
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken);

        if (asset == null)
        {
            return Results.NotFound(new { error = $"Asset with ID {assetId} not found" });
        }

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
        var motionPath = ResolveMotionClipPath(physicalPath);

        if (motionPath == null)
        {
            return Results.NotFound(new { error = $"Asset {assetId} has no paired motion clip" });
        }

        return Results.File(motionPath, "video/quicktime", enableRangeProcessing: true);
    }

    /// <summary>
    /// Returns the path to the sibling motion clip ({basename}.mov / .MOV) next
    /// to <paramref name="stillPath"/>, or null when none exists. Tries the
    /// common casings explicitly since the host filesystem may be case-sensitive.
    /// </summary>
    private static string? ResolveMotionClipPath(string stillPath)
    {
        var directory = Path.GetDirectoryName(stillPath);
        if (string.IsNullOrEmpty(directory))
        {
            return null;
        }

        var baseName = Path.GetFileNameWithoutExtension(stillPath);
        foreach (var ext in new[] { ".mov", ".MOV", ".mp4", ".MP4" })
        {
            var candidate = Path.Combine(directory, baseName + ext);
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        return null;
    }
}
