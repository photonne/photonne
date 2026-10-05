using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Folders;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Formats.Jpeg;
using SixLabors.ImageSharp.Metadata.Profiles.Exif;

namespace Photonne.Server.Api.Features.AssetDetail;

/// <summary>
/// "Elegir fotograma": browse a motion photo's clip frame by frame and save the
/// chosen frame as a NEW photo next to the original (the original is never
/// touched). Frames are decoded server-side (<see cref="MotionFrameService"/>)
/// so every client — desktop included, which has no video player — shows the
/// same pictures through plain image requests.
/// </summary>
public class MotionFramesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/motion/frames", HandleList)
            .WithName("GetAssetMotionFrames")
            .WithTags("Assets")
            .WithDescription("Decodes a motion photo's clip and returns how many frames it has")
            .RequireAuthorization();

        app.MapGet("/api/assets/{assetId:guid}/motion/frames/{index:int}", HandleFrame)
            .WithName("GetAssetMotionFrame")
            .WithTags("Assets")
            .WithDescription("Gets one preview frame of a motion photo's clip as JPEG")
            .RequireAuthorization();

        app.MapPost("/api/assets/{assetId:guid}/motion/frames/{index:int}/save", HandleSave)
            .WithName("SaveAssetMotionFrame")
            .WithTags("Assets")
            .WithDescription("Saves one frame of a motion photo's clip as a new photo next to the original")
            .RequireAuthorization();
    }

    /// <summary>
    /// The caller may save a frame of <paramref name="asset"/>: a live motion
    /// photo whose folder they can write to, outside read-only external
    /// libraries. The detail endpoint uses it to offer the action at all.
    /// </summary>
    internal static async Task<bool> CanSaveFrameAsync(
        ApplicationDbContext dbContext, Asset asset, ClaimsPrincipal user, CancellationToken ct)
    {
        if (asset.DeletedAt != null || asset.ExternalLibraryId.HasValue) return false;
        if (asset.FolderId is not { } folderId) return false;
        if (!asset.Tags.Any(t => t.TagType == AssetTagType.LivePhoto)) return false;
        return await FoldersEndpoint.CanWriteFolderAsync(
            dbContext, user.GetUserId(), folderId, user.IsInRole("Admin"), ct);
    }

    private static async Task<IResult> HandleList(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromServices] MotionFrameService frameService,
        [FromRoute] Guid assetId,
        CancellationToken cancellationToken)
    {
        var frames = await LoadFramesAsync(dbContext, settingsService, frameService, assetId, cancellationToken);
        return frames == null
            ? Results.NotFound(new { error = $"Asset {assetId} has no motion clip" })
            : Results.Ok(new MotionFramesResponse { FrameCount = frames.FrameCount });
    }

    private static async Task<IResult> HandleFrame(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromServices] MotionFrameService frameService,
        [FromRoute] Guid assetId,
        [FromRoute] int index,
        HttpContext httpContext,
        CancellationToken cancellationToken)
    {
        var frames = await LoadFramesAsync(dbContext, settingsService, frameService, assetId, cancellationToken);
        if (frames == null || index < 0 || index >= frames.FrameCount)
        {
            return Results.NotFound(new { error = $"Asset {assetId} has no motion frame {index}" });
        }

        // The client steps back and forth over the same few dozen frames.
        httpContext.Response.Headers.CacheControl = "private, max-age=3600";
        return Results.File(frames.FramePath(index), "image/jpeg");
    }

    private static async Task<IResult> HandleSave(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromServices] MotionFrameService frameService,
        [FromServices] FileHashService hashService,
        [FromServices] IEnrichmentService enrichmentService,
        [FromServices] ILogger<MotionFramesEndpoint> logger,
        [FromRoute] Guid assetId,
        [FromRoute] int index,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userId = user.GetUserId();
        var source = await dbContext.Assets
            .AsNoTracking()
            .Include(a => a.Exif)
            .Include(a => a.Tags)
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken);
        if (source == null || source.DeletedAt != null)
        {
            return Results.NotFound(new { error = $"Asset with ID {assetId} not found" });
        }

        if (!await CanSaveFrameAsync(dbContext, source, user, cancellationToken))
        {
            return Results.Problem(
                detail: "No tienes permiso para añadir fotos a la carpeta de esta foto.",
                statusCode: StatusCodes.Status403Forbidden);
        }

        var stillPath = await settingsService.ResolvePhysicalPathAsync(source.FullPath);
        using var frame = index < 0
            ? null
            : await frameService.ExtractFullFrameAsync(stillPath, index, cancellationToken);
        if (frame == null)
        {
            return Results.NotFound(new { error = $"Asset {assetId} has no motion frame {index}" });
        }

        frame.Metadata.ExifProfile = BuildExif(source);

        var directory = Path.GetDirectoryName(stillPath)!;
        var targetPath = UniquePath(directory, $"{Path.GetFileNameWithoutExtension(stillPath)}_frame{index + 1:000}", ".jpg");
        try
        {
            await frame.SaveAsJpegAsync(targetPath, new JpegEncoder { Quality = 95 }, cancellationToken);

            // Same file dates as the original so a re-index from disk sorts the
            // frame next to it too.
            File.SetCreationTimeUtc(targetPath, source.FileCreatedAt);
            File.SetLastWriteTimeUtc(targetPath, source.FileModifiedAt);

            var fileInfo = new FileInfo(targetPath);
            var asset = new Asset
            {
                FileName = fileInfo.Name,
                FullPath = await settingsService.VirtualizePathAsync(targetPath),
                FileSize = fileInfo.Length,
                Checksum = await hashService.CalculateFileHashAsync(targetPath, cancellationToken),
                Type = AssetType.Image,
                Extension = ".jpg",
                FileCreatedAt = source.FileCreatedAt,
                FileModifiedAt = source.FileModifiedAt,
                // The frame IS the same moment: it inherits the original's date
                // and its provenance (a date fixed by hand stays fixed).
                CapturedAt = source.CapturedAt,
                CapturedAtSource = source.CapturedAtSource,
                ScannedAt = DateTime.UtcNow,
                FolderId = source.FolderId,
                OwnerId = userId
            };
            dbContext.Assets.Add(asset);
            await dbContext.SaveChangesAsync(cancellationToken);

            // Same pipeline as an upload: EXIF (reads back what BuildExif wrote),
            // media tags, thumbnails; ML follows the thumbnails.
            await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Exif, cancellationToken);
            await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.MediaRecognition, cancellationToken);
            await enrichmentService.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails, cancellationToken);

            return Results.Ok(new SaveMotionFrameResponse { AssetId = asset.Id });
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            logger.LogError(ex, "Saving frame {Index} of asset {AssetId} failed", index, assetId);
            if (File.Exists(targetPath)) File.Delete(targetPath);
            return Results.Problem(ex.Message);
        }
    }

    private static async Task<MotionFrameSet?> LoadFramesAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        MotionFrameService frameService,
        Guid assetId,
        CancellationToken cancellationToken)
    {
        var fullPath = await dbContext.Assets
            .Where(a => a.Id == assetId && a.DeletedAt == null)
            .Select(a => a.FullPath)
            .FirstOrDefaultAsync(cancellationToken);
        if (fullPath == null) return null;

        var stillPath = await settingsService.ResolvePhysicalPathAsync(fullPath);
        return await frameService.GetPreviewFramesAsync(assetId, stillPath, cancellationToken);
    }

    /// <summary>
    /// The original's capture date, camera and — only when measured, never
    /// interpolated — its GPS fix. The frame comes out of ffmpeg already upright,
    /// so no orientation tag.
    /// </summary>
    private static ExifProfile BuildExif(Asset source)
    {
        var profile = new ExifProfile();

        // CapturedAt is the local wall-clock, the same frame EXIF dates use.
        var date = source.CapturedAt.ToString("yyyy:MM:dd HH:mm:ss");
        profile.SetValue(ExifTag.DateTimeOriginal, date);
        profile.SetValue(ExifTag.DateTimeDigitized, date);
        profile.SetValue(ExifTag.DateTime, date);

        var exif = source.Exif;
        if (exif == null) return profile;

        if (!string.IsNullOrWhiteSpace(exif.CameraMake)) profile.SetValue(ExifTag.Make, exif.CameraMake);
        if (!string.IsNullOrWhiteSpace(exif.CameraModel)) profile.SetValue(ExifTag.Model, exif.CameraModel);

        if (exif.LocationSource >= LocationSource.Exif &&
            exif.Latitude is { } latitude && exif.Longitude is { } longitude)
        {
            profile.SetValue(ExifTag.GPSLatitudeRef, latitude >= 0 ? "N" : "S");
            profile.SetValue(ExifTag.GPSLatitude, ToDegreesMinutesSeconds(latitude));
            profile.SetValue(ExifTag.GPSLongitudeRef, longitude >= 0 ? "E" : "W");
            profile.SetValue(ExifTag.GPSLongitude, ToDegreesMinutesSeconds(longitude));
            if (exif.Altitude is { } altitude)
            {
                profile.SetValue(ExifTag.GPSAltitudeRef, (byte)(altitude >= 0 ? 0 : 1));
                profile.SetValue(ExifTag.GPSAltitude, new Rational(Math.Abs(altitude)));
            }
        }

        return profile;
    }

    private static Rational[] ToDegreesMinutesSeconds(double coordinate)
    {
        var value = Math.Abs(coordinate);
        var degrees = Math.Floor(value);
        var minutes = Math.Floor((value - degrees) * 60);
        var seconds = (value - degrees - minutes / 60) * 3600;
        return
        [
            new Rational((uint)degrees, 1),
            new Rational((uint)minutes, 1),
            new Rational((uint)Math.Round(seconds * 10000), 10000)
        ];
    }

    private static string UniquePath(string directory, string baseName, string extension)
    {
        var candidate = Path.Combine(directory, baseName + extension);
        for (var n = 2; File.Exists(candidate); n++)
        {
            candidate = Path.Combine(directory, $"{baseName}_{n}{extension}");
        }
        return candidate;
    }
}

public class MotionFramesResponse
{
    public int FrameCount { get; set; }
}

public class SaveMotionFrameResponse
{
    public Guid AssetId { get; set; }
}
