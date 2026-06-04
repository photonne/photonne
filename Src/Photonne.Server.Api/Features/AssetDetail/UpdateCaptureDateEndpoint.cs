using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetDetail;

/// <summary>
/// Lets a user override the capture date of a single asset. Updates the
/// timeline-ordering column (<see cref="Asset.CapturedAt"/>) and the stored
/// EXIF <see cref="AssetExif.DateTimeOriginal"/>, and — when requested and the
/// asset is writable — writes the date back into the physical file's EXIF.
/// </summary>
public class UpdateCaptureDateEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPatch("/api/assets/{assetId:guid}/date", Handle)
            .WithName("UpdateAssetCaptureDate")
            .WithTags("Assets")
            .WithDescription("Updates the capture date of an asset (timeline order + EXIF), optionally writing it back to the file.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] ExifWriterService exifWriter,
        [FromServices] SettingsService settingsService,
        [FromServices] FileHashService fileHashService,
        [FromRoute] Guid assetId,
        [FromBody] UpdateCaptureDateRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out _))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var asset = await dbContext.Assets
            .Include(a => a.Exif)
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, ct);

        if (asset == null)
            return Results.NotFound(new { error = "Asset no encontrado." });

        if (!IsAssetInUserRoot(asset.FullPath, username))
            return Results.Forbid();

        // The client sends an absolute instant; persist it as UTC to match the
        // EXIF extractor, which stores DateTimeOriginal/CapturedAt in UTC.
        var dateUtc = DateTime.SpecifyKind(request.DateTaken.UtcDateTime, DateTimeKind.Utc);

        // ── Database: EXIF row + timeline column ──────────────────────────────
        if (asset.Exif == null)
        {
            asset.Exif = new AssetExif { AssetId = asset.Id };
            dbContext.AssetExifs.Add(asset.Exif);
        }
        asset.Exif.DateTimeOriginal = dateUtc;
        asset.CapturedAt = dateUtc;
        // Manual is the top of the provenance ladder: no automated pass
        // (extraction fallback, restore, inference) may overwrite it.
        asset.CapturedAtSource = CaptureDateSource.Manual;

        // ── Optional physical-file write ──────────────────────────────────────
        bool fileWritten = false;
        string? reason = null;

        if (request.WriteToFile)
        {
            if (asset.ExternalLibraryId.HasValue)
            {
                reason = "La biblioteca externa es de solo lectura; solo se actualizó la base de datos.";
            }
            else
            {
                // EXIF where the format supports it; the file mtime ALWAYS
                // (videos/RAW included) so the physical file carries the date
                // even where EXIF can't be written.
                var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                var result = await exifWriter.ApplyDateToFileAsync(physicalPath, dateUtc, ct);
                fileWritten = result.FileTouched;
                reason = result.Reason;

                // The file changed on disk: keep checksum/size/mtime in sync so the
                // next library scan doesn't flag it and duplicate detection stays
                // coherent. The checksum only changes when EXIF was embedded.
                if (result.FileTouched && File.Exists(physicalPath))
                {
                    var info = new FileInfo(physicalPath);
                    asset.FileSize = info.Length;
                    asset.FileModifiedAt = info.LastWriteTimeUtc;
                    if (result.ExifWritten)
                    {
                        asset.Checksum = await fileHashService.CalculateFileHashAsync(physicalPath, ct);
                    }
                }
            }
        }

        await dbContext.SaveChangesAsync(ct);

        return Results.Ok(new
        {
            dateTaken = dateUtc,
            capturedAt = dateUtc,
            fileWritten,
            reason
        });
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }

    private static bool IsAssetInUserRoot(string assetPath, string username)
    {
        var normalized = assetPath.Replace('\\', '/');
        return normalized.Contains($"/users/{username}/", StringComparison.OrdinalIgnoreCase);
    }
}

public class UpdateCaptureDateRequest
{
    /// <summary>New capture instant (absolute time, e.g. ISO-8601 with offset/Z).</summary>
    public DateTimeOffset DateTaken { get; set; }

    /// <summary>When true, also write the date into the physical file's EXIF.</summary>
    public bool WriteToFile { get; set; }
}
