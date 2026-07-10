using ImageMagick;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Result of an attempt to write the capture date back into the physical file.
/// <see cref="FileWritten"/> is false when the format doesn't support a reliable
/// EXIF write (video, RAW, or a Magick delegate failure) — in that case the
/// caller has still updated the database, and <see cref="Reason"/> explains why
/// the file itself was left untouched.
/// </summary>
public record ExifWriteResult(bool FileWritten, string? Reason = null);

/// <summary>
/// Result of <see cref="ExifWriterService.ApplyDateToFileAsync"/>: EXIF written
/// (images only) and/or the file's mtime set to the capture date (every type,
/// videos included — the universal carrier that survives copies and rebuilds).
/// </summary>
public record PhysicalDateResult(bool ExifWritten, bool ModifiedTimeSet, string? Reason = null)
{
    public bool FileTouched => ExifWritten || ModifiedTimeSet;
}

/// <summary>
/// Writes the capture date (EXIF DateTimeOriginal / DateTimeDigitized / DateTime)
/// back into the physical image file using Magick.NET. The complementary
/// <see cref="ExifExtractorService"/> reads the same tags. Both sides interpret
/// the EXIF date as wall-clock time in the configured <c>DefaultTimezone</c> and
/// convert to/from UTC, so a value written here round-trips through a later
/// re-extraction unchanged.
/// </summary>
public class ExifWriterService
{
    private readonly SettingsService _settingsService;

    public ExifWriterService(SettingsService settingsService)
    {
        _settingsService = settingsService;
    }

    /// <summary>
    /// Writes <paramref name="dateTakenLocal"/> into the file at <paramref name="filePath"/>.
    /// The value is stored in the database as the photo's LOCAL wall-clock
    /// (naive), which is exactly the EXIF "yyyy:MM:dd HH:mm:ss" representation —
    /// so it is written out verbatim, with no timezone conversion (the extractor
    /// now reads it back the same way).
    /// </summary>
    public Task<ExifWriteResult> WriteDateTakenAsync(
        string filePath,
        DateTime dateTakenLocal,
        CancellationToken cancellationToken = default)
    {
        var extension = Path.GetExtension(filePath).ToLowerInvariant();

        if (IsVideoFile(extension))
            return Task.FromResult(new ExifWriteResult(false, "La escritura de metadatos en vídeos no está soportada; solo se actualizó la base de datos."));

        if (!IsWritableImage(extension))
            return Task.FromResult(new ExifWriteResult(false, $"El formato {extension} no admite escritura de EXIF fiable; solo se actualizó la base de datos."));

        if (!File.Exists(filePath))
            return Task.FromResult(new ExifWriteResult(false, "El archivo físico no existe; solo se actualizó la base de datos."));

        var exifString = dateTakenLocal.ToString("yyyy:MM:dd HH:mm:ss");
        return WriteExifStringAsync(filePath, exifString, cancellationToken);
    }

    private static async Task<ExifWriteResult> WriteExifStringAsync(
        string filePath, string exifString, CancellationToken cancellationToken)
    {
        try
        {
            await Task.Run(() =>
            {
                using var image = new MagickImage(filePath);
                var profile = image.GetExifProfile() ?? new ExifProfile();

                // DateTimeOriginal is the capture time the extractor prioritises;
                // DateTimeDigitized and DateTime (IFD0) are kept in sync so other
                // tools that read a different tag see a consistent value.
                profile.SetValue(ExifTag.DateTimeOriginal, exifString);
                profile.SetValue(ExifTag.DateTimeDigitized, exifString);
                profile.SetValue(ExifTag.DateTime, exifString);

                image.SetProfile(profile);
                image.Write(filePath);
            }, cancellationToken);

            return new ExifWriteResult(true);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[EXIF-WRITE] No se pudo escribir la fecha en {filePath}: {ex.Message}");
            return new ExifWriteResult(false, $"No se pudo escribir el EXIF en el archivo ({ex.Message}); solo se actualizó la base de datos.");
        }
    }

    /// <summary>
    /// Makes the physical file itself carry <paramref name="dateTakenUtc"/>:
    /// writes the EXIF tags where supported (JPEG/PNG/HEIC/WebP/TIFF) and sets
    /// the filesystem mtime on EVERY type — videos and RAW included, where
    /// EXIF writing isn't possible. The mtime is the lossless universal
    /// fallback: rsync preserves it, every photo tool reads it, and a future
    /// re-index would re-derive the correct date even from a fresh database.
    /// </summary>
    public async Task<PhysicalDateResult> ApplyDateToFileAsync(
        string filePath,
        DateTime dateTakenLocal,
        CancellationToken cancellationToken = default)
    {
        if (!File.Exists(filePath))
            return new PhysicalDateResult(false, false, "El archivo físico no existe; solo se actualizó la base de datos.");

        var exif = await WriteDateTakenAsync(filePath, dateTakenLocal, cancellationToken);

        // The EXIF tags carry the wall-clock verbatim, but the filesystem mtime
        // is a genuine instant — convert the local wall-clock to real UTC so a
        // later re-index (which turns the mtime back INTO local wall-clock) round-trips.
        var tz = await MetadataTimeZone.ResolveAsync(_settingsService, cancellationToken);
        var mtimeUtc = TimeZoneInfo.ConvertTimeToUtc(
            DateTime.SpecifyKind(dateTakenLocal, DateTimeKind.Unspecified), tz);

        bool mtimeSet = false;
        try
        {
            File.SetLastWriteTimeUtc(filePath, DateTime.SpecifyKind(mtimeUtc, DateTimeKind.Utc));
            mtimeSet = true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[EXIF-WRITE] No se pudo ajustar el mtime de {filePath}: {ex.Message}");
        }

        var reason = exif.FileWritten
            ? null
            : mtimeSet
                ? "Sin escritura EXIF para este formato; se ajustó la fecha de modificación del archivo."
                : exif.Reason;

        return new PhysicalDateResult(exif.FileWritten, mtimeSet, reason);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static TimeZoneInfo ResolveTimezone(string tzId)
    {
        if (string.IsNullOrWhiteSpace(tzId) || tzId.Equals("UTC", StringComparison.OrdinalIgnoreCase))
            return TimeZoneInfo.Utc;

        try { return TimeZoneInfo.FindSystemTimeZoneById(tzId); }
        catch
        {
            try
            {
                if (TimeZoneInfo.TryConvertIanaIdToWindowsId(tzId, out var winId))
                    return TimeZoneInfo.FindSystemTimeZoneById(winId!);
            }
            catch { /* ignore */ }

            return TimeZoneInfo.Utc;
        }
    }

    private static bool IsVideoFile(string extension)
    {
        var exts = new[] { ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm", ".m4v",
                           ".3gp", ".mpeg", ".mpg", ".3g2", ".3gpp", ".amv", ".asf",
                           ".f4v", ".m2v", ".mp2", ".mpe", ".mpv", ".ogv", ".qt", ".vob" };
        return exts.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }

    // Formats where Magick.NET can reliably round-trip an EXIF profile. RAW
    // formats (cr2, nef, arw, dng, …) are intentionally excluded — they're read
    // as opaque containers and re-encoding them would be destructive.
    private static bool IsWritableImage(string extension)
    {
        var exts = new[] { ".jpg", ".jpeg", ".tiff", ".tif", ".png", ".webp", ".heic", ".heif" };
        return exts.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }
}
