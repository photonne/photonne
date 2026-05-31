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
    /// Writes <paramref name="dateTakenUtc"/> into the file at <paramref name="filePath"/>.
    /// The value is stored in the database as UTC; here it is converted back to the
    /// configured timezone (matching how the extractor read it) before formatting
    /// to the EXIF "yyyy:MM:dd HH:mm:ss" representation.
    /// </summary>
    public async Task<ExifWriteResult> WriteDateTakenAsync(
        string filePath,
        DateTime dateTakenUtc,
        CancellationToken cancellationToken = default)
    {
        var extension = Path.GetExtension(filePath).ToLowerInvariant();

        if (IsVideoFile(extension))
            return new ExifWriteResult(false, "La escritura de metadatos en vídeos no está soportada; solo se actualizó la base de datos.");

        if (!IsWritableImage(extension))
            return new ExifWriteResult(false, $"El formato {extension} no admite escritura de EXIF fiable; solo se actualizó la base de datos.");

        if (!File.Exists(filePath))
            return new ExifWriteResult(false, "El archivo físico no existe; solo se actualizó la base de datos.");

        var tzInfo = ResolveTimezone(await _settingsService.GetSettingAsync(
            ExifExtractorService.KeyDefaultTimezone, Guid.Empty, "UTC"));

        // DB stores UTC; EXIF stores local wall-clock in the configured timezone.
        var local = TimeZoneInfo.ConvertTimeFromUtc(
            DateTime.SpecifyKind(dateTakenUtc, DateTimeKind.Utc), tzInfo);
        var exifString = local.ToString("yyyy:MM:dd HH:mm:ss");

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
