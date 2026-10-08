using System.Security.Claims;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.UploadAssets;

public record MotionClipsMissingRequest(List<Guid> AssetIds);
public record MotionClipsMissingResponse(List<Guid> Missing);

/// <summary>
/// <see cref="AssetId"/> is the new clip's asset; it is left out (not null)
/// when the still already had its clip, as the apps have always received it.
/// </summary>
public record MotionClipAttachResponse(
    string Message,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] Guid? AssetId);

/// <summary>
/// The motion half of an iOS Live Photo. PhotoKit keeps the still and its clip
/// as two resources of ONE asset, so the phone backup uploads the still through
/// <c>/upload</c> and then hands the clip over here. It lands next to the still
/// with the still's final name (<c>IMG_1234.HEIC</c> → <c>IMG_1234.mov</c>, or
/// <c>{guid}_IMG_1234.mov</c> when the still was renamed on collision) — the
/// sibling pairing <see cref="MediaRecognitionService"/> and the /motion
/// endpoint already rely on.
/// </summary>
public class MotionClipEndpoints : IEndpoint
{
    private const int MaxAssetIdsPerRequest = 1000;
    private static readonly string[] ClipExtensions = { ".mov", ".mp4" };

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/{assetId:guid}/motion-clip", HandleAttach)
            .DisableAntiforgery()
            .WithName("AttachMotionClip")
            .WithTags("Assets")
            .WithDescription("Stores the paired motion clip of an uploaded Live Photo still next to it")
            .Produces<ApiError>(StatusCodes.Status409Conflict)
            .Produces<ApiError>(StatusCodes.Status413PayloadTooLarge)
            .RequireAuthorization()
            .RequireRateLimiting("demo-upload");

        app.MapPost("/api/assets/motion-clips/missing", HandleMissing)
            .WithName("MotionClipsMissing")
            .WithTags("Assets")
            .WithDescription("Returns which of the caller's stills are not paired with a motion clip yet")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<MotionClipAttachResponse>, BadRequest<ApiError>, NotFound<ApiError>, ForbidHttpResult, JsonHttpResult<ApiError>>> HandleAttach(
        [FromRoute] Guid assetId,
        [FromForm] IFormFile file,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] FileHashService hashService,
        [FromServices] IEnrichmentService enrichmentService,
        [FromServices] SettingsService settingsService,
        [FromServices] ILogger<MotionClipEndpoints> logger,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (file == null || file.Length == 0)
            return TypedResults.BadRequest(new ApiError("No file uploaded", "no_file_uploaded"));

        var userId = user.GetUserId();
        var still = await dbContext.Assets
            .Include(a => a.Tags)
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, cancellationToken);
        if (still == null)
            return TypedResults.NotFound(new ApiError($"Asset with ID {assetId} not found", "asset_not_found"));
        if (still.OwnerId != userId || still.ExternalLibraryId.HasValue)
            return TypedResults.Forbid();
        if (still.Type != AssetType.Image)
            return TypedResults.BadRequest(new ApiError("Only a still photo can carry a motion clip", "not_a_still_photo"));

        var stillPath = await settingsService.ResolvePhysicalPathAsync(still.FullPath);
        if (!File.Exists(stillPath))
            return TypedResults.NotFound(new ApiError($"Asset {assetId} has no file on disk", "file_not_found"));

        // Idempotent: a retry, or a still whose clip arrived another way, is
        // already paired. Only the tag may be missing.
        if (MotionFrameService.ResolveSiblingClipPath(stillPath) != null)
        {
            await TagLivePhotoAsync(dbContext, still, cancellationToken);
            return TypedResults.Ok(new MotionClipAttachResponse("Motion clip already present", null));
        }

        var limitError = await UploadAssetsEndpoint.CheckUploadLimitsAsync(
            dbContext, settingsService, userId, file.Length, cancellationToken);
        if (limitError != null) return limitError;

        var targetPath = ClipPathFor(stillPath, file.FileName);
        var tempPath = targetPath + ".part";
        var stored = false;
        try
        {
            await using (var stream = new FileStream(tempPath, FileMode.CreateNew))
            {
                await file.CopyToAsync(stream, cancellationToken);
            }
            File.Move(tempPath, targetPath);
            stored = true;
            // Same dates as the still so a re-index from disk keeps them together.
            File.SetCreationTimeUtc(targetPath, still.FileCreatedAt);
            File.SetLastWriteTimeUtc(targetPath, still.FileModifiedAt);

            var fileInfo = new FileInfo(targetPath);
            var extension = fileInfo.Extension.ToLowerInvariant();
            var clip = new Asset
            {
                FileName = fileInfo.Name,
                FullPath = await settingsService.VirtualizePathAsync(targetPath),
                FileSize = fileInfo.Length,
                Checksum = await hashService.CalculateFileHashAsync(targetPath, cancellationToken),
                Type = MediaFileTypes.Classify(extension),
                Extension = extension,
                FileCreatedAt = still.FileCreatedAt,
                FileModifiedAt = still.FileModifiedAt,
                CapturedAt = still.CapturedAt,
                ScannedAt = DateTime.UtcNow,
                FolderId = still.FolderId,
                OwnerId = userId
            };
            dbContext.Assets.Add(clip);
            // Tagged right away instead of waiting for the media-recognition
            // worker: until then the clip would surface in the timeline as a
            // loose video, and the still would still lack its icon.
            dbContext.AssetTags.Add(new AssetTag
            {
                AssetId = clip.Id,
                TagType = AssetTagType.MotionPhotoPart
            });
            await dbContext.SaveChangesAsync(cancellationToken);
            // From here on the row owns the file: a later failure keeps both.
            stored = false;
            await TagLivePhotoAsync(dbContext, still, cancellationToken);

            await enrichmentService.EnqueueAsync(clip.Id, AssetEnrichmentType.Exif, cancellationToken);
            await enrichmentService.EnqueueAsync(clip.Id, AssetEnrichmentType.MediaRecognition, cancellationToken);
            await enrichmentService.EnqueueAsync(clip.Id, AssetEnrichmentType.Thumbnails, cancellationToken);

            return TypedResults.Ok(new MotionClipAttachResponse("Motion clip stored", clip.Id));
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            logger.LogError(ex, "Storing the motion clip of asset {AssetId} failed", assetId);
            // A clip on disk without its row would read as "already present"
            // on the retry and never get indexed.
            if (stored && File.Exists(targetPath)) File.Delete(targetPath);
            throw;
        }
        finally
        {
            if (File.Exists(tempPath)) File.Delete(tempPath);
        }
    }

    private static async Task<Results<Ok<MotionClipsMissingResponse>, BadRequest<ApiError>>> HandleMissing(
        MotionClipsMissingRequest request,
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return TypedResults.Ok(new MotionClipsMissingResponse([]));
        if (request.AssetIds.Count > MaxAssetIdsPerRequest)
            return TypedResults.BadRequest(new ApiError($"Too many asset ids; maximum is {MaxAssetIdsPerRequest} per request", "too_many_items"));

        var userId = user.GetUserId();
        var requested = request.AssetIds.ToHashSet();

        // The LivePhoto tag is the cheap stand-in for "has its clip": the
        // attach endpoint sets it together with the file. A still whose clip
        // is on disk but not tagged yet just gets an idempotent attach back.
        var missing = await dbContext.Assets
            .Where(a => requested.Contains(a.Id)
                     && a.OwnerId == userId
                     && a.DeletedAt == null
                     && a.ExternalLibraryId == null
                     && a.Type == AssetType.Image
                     && !a.Tags.Any(t => t.TagType == AssetTagType.LivePhoto))
            .Select(a => a.Id)
            .ToListAsync(cancellationToken);

        return TypedResults.Ok(new MotionClipsMissingResponse(missing));
    }

    /// <summary>
    /// Where the clip of <paramref name="stillPath"/> goes: same folder, same
    /// base name, the uploaded clip's extension in lower case (.mov unless it
    /// is an .mp4). Lower case because the sibling lookup only tries the
    /// all-lower and all-upper spellings.
    /// </summary>
    internal static string ClipPathFor(string stillPath, string? uploadedFileName)
    {
        var uploadedExtension = Path.GetExtension(uploadedFileName ?? string.Empty).ToLowerInvariant();
        var extension = ClipExtensions.Contains(uploadedExtension) ? uploadedExtension : ".mov";
        return Path.Combine(
            Path.GetDirectoryName(stillPath)!,
            Path.GetFileNameWithoutExtension(stillPath) + extension);
    }

    private static async Task TagLivePhotoAsync(
        ApplicationDbContext dbContext, Asset still, CancellationToken cancellationToken)
    {
        if (still.Tags.Any(t => t.TagType == AssetTagType.LivePhoto)) return;
        dbContext.AssetTags.Add(new AssetTag { AssetId = still.Id, TagType = AssetTagType.LivePhoto });
        await dbContext.SaveChangesAsync(cancellationToken);
    }
}
