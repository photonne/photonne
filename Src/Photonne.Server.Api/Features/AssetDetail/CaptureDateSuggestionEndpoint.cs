using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetDetail;

/// <summary>
/// Per-asset "what date would we recover?" preview. Re-reads the physical
/// file's EXIF and runs the filename/folder inference, WITHOUT changing
/// anything — the client shows both candidates so the user can verify and
/// apply the one they trust through the regular edit-date flow. This is the
/// single-asset counterpart of the bulk restore task's dry-run.
/// </summary>
public class CaptureDateSuggestionEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/date/suggestion", Handle)
            .WithName("GetAssetCaptureDateSuggestion")
            .WithTags("Assets")
            .WithDescription("Previews the capture date recoverable from the file's EXIF and from the file name/folder path, without applying anything.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] ExifExtractorService exifExtractor,
        [FromServices] CaptureDateInferenceService inference,
        [FromServices] SettingsService settingsService,
        [FromRoute] Guid assetId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var asset = await dbContext.Assets
            .AsNoTracking()
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, ct);

        if (asset == null)
            return Results.NotFound(new { error = "Asset no encontrado." });

        // Same ownership rule as the edit-date endpoint: suggestions only make
        // sense where the user could apply them.
        if (!asset.FullPath.Replace('\\', '/')
                .Contains($"/users/{username}/", StringComparison.OrdinalIgnoreCase))
        {
            return Results.Forbid();
        }

        // EXIF candidate — re-read from disk (tolerant to missing files).
        // The filesystem candidate is the effective file date: the OLDER of
        // birthtime/mtime, because copies (rsync) rewrite the birthtime while
        // preserving mtime — for EXIF-less files (PNGs!) mtime is usually the
        // only surviving trace of the real date.
        DateTime? exifDate = null;
        DateTime? fileDate;
        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
        if (File.Exists(physicalPath))
        {
            var exif = await exifExtractor.ExtractExifAsync(physicalPath, ct);
            exifDate = exif?.DateTimeOriginal;

            var info = new FileInfo(physicalPath);
            fileDate = info.CreationTimeUtc <= info.LastWriteTimeUtc
                ? info.CreationTimeUtc
                : info.LastWriteTimeUtc;
        }
        else
        {
            // File unreachable from this process — fall back to the dates
            // captured at index time.
            fileDate = asset.EffectiveFileCreatedAt;
        }

        // Inference candidate — file name first, folder path second.
        var inferred = await inference.TryInferAsync(asset.FileName, asset.FullPath, ct);

        return Results.Ok(new CaptureDateSuggestionResponse
        {
            CurrentDate = asset.CapturedAt,
            CurrentSource = asset.CapturedAtSource.ToString(),
            ExifDate = exifDate,
            InferredDate = inferred?.DateUtc,
            InferredOrigin = inferred?.Origin.ToString(),
            FileDate = fileDate
        });
    }
}

public class CaptureDateSuggestionResponse
{
    public DateTime CurrentDate { get; set; }
    public string CurrentSource { get; set; } = string.Empty;
    /// <summary>DateTimeOriginal re-read from the physical file, if any.</summary>
    public DateTime? ExifDate { get; set; }
    /// <summary>Date inferred from the file name or folder path, if any.</summary>
    public DateTime? InferredDate { get; set; }
    /// <summary>"FileName" or "FolderPath" when <see cref="InferredDate"/> is set.</summary>
    public string? InferredOrigin { get; set; }

    /// <summary>Effective filesystem date — the older of birthtime/mtime,
    /// re-read live from disk (DB snapshot when the file is unreachable).</summary>
    public DateTime? FileDate { get; set; }
}
