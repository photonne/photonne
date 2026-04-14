using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using MetadataExtractor;
using MetadataExtractor.Formats.Exif;
using MetadataExtractor.Formats.Iptc;
using MetadataExtractor.Formats.Xmp;
using Xabe.FFmpeg;

namespace Photonne.Server.Api.Shared.Services;

public class ExifExtractorService
{
    private readonly SettingsService _settingsService;

    public ExifExtractorService(SettingsService settingsService)
    {
        _settingsService = settingsService;
    }

    // ── Settings keys ─────────────────────────────────────────────────────────
    public const string KeyExtractCameraInfo = "MetadataSettings.ExtractCameraInfo";
    public const string KeyExtractGps        = "MetadataSettings.ExtractGps";
    public const string KeyExtractIptc       = "MetadataSettings.ExtractIptc";
    public const string KeyExtractDateTime   = "MetadataSettings.ExtractDateTime";
    public const string KeyDefaultTimezone   = "MetadataSettings.DefaultTimezone";
    public const string KeyReadXmpSidecar    = "MetadataSettings.ReadXmpSidecar";

    // ── Options record ────────────────────────────────────────────────────────
    private record ExtractionOptions(
        bool CameraInfo,
        bool Gps,
        bool Iptc,
        bool DateTime,
        string DefaultTimezone,
        bool ReadXmpSidecar);

    private async Task<ExtractionOptions> LoadOptionsAsync()
    {
        bool ParseBool(string v, bool def) =>
            string.IsNullOrEmpty(v) ? def : !v.Equals("false", StringComparison.OrdinalIgnoreCase);

        return new ExtractionOptions(
            CameraInfo:     ParseBool(await _settingsService.GetSettingAsync(KeyExtractCameraInfo, Guid.Empty, "true"),  true),
            Gps:            ParseBool(await _settingsService.GetSettingAsync(KeyExtractGps,        Guid.Empty, "true"),  true),
            Iptc:           ParseBool(await _settingsService.GetSettingAsync(KeyExtractIptc,       Guid.Empty, "true"),  true),
            DateTime:       ParseBool(await _settingsService.GetSettingAsync(KeyExtractDateTime,   Guid.Empty, "true"),  true),
            DefaultTimezone: await _settingsService.GetSettingAsync(KeyDefaultTimezone,            Guid.Empty, "UTC"),
            ReadXmpSidecar: ParseBool(await _settingsService.GetSettingAsync(KeyReadXmpSidecar,    Guid.Empty, "false"), false));
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /// <summary>
    /// Extracts EXIF/IPTC/XMP metadata from an image or video file,
    /// respecting the global MetadataSettings configuration.
    /// </summary>
    public async Task<AssetExif?> ExtractExifAsync(string filePath, CancellationToken cancellationToken = default)
    {
        try
        {
            var extension = Path.GetExtension(filePath).ToLowerInvariant();
            if (!IsImageFile(extension) && !IsVideoFile(extension))
                return null;

            var options = await LoadOptionsAsync();

            IEnumerable<MetadataExtractor.Directory> directories;
            try
            {
                directories = await Task.Run(() => ImageMetadataReader.ReadMetadata(filePath), cancellationToken);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DEBUG] Metadata extraction failed for {filePath}: {ex.Message}");
                return new AssetExif();
            }

            // Optionally append sidecar .xmp directories
            if (options.ReadXmpSidecar && IsImageFile(extension))
            {
                var sidecarDirs = ReadXmpSidecar(filePath);
                if (sidecarDirs.Any())
                    directories = directories.Concat(sidecarDirs);
            }

            var exif = new AssetExif();
            var tzInfo = ResolveTimezone(options.DefaultTimezone);

            foreach (var directory in directories)
            {
                ExtractDirectoryMetadata(directory, exif, options, tzInfo);
            }

            // Dimensions
            if (IsImageFile(extension))
            {
                try
                {
                    var imageInfo = await Task.Run(() => SixLabors.ImageSharp.Image.Identify(filePath), cancellationToken);
                    if (imageInfo != null)
                    {
                        exif.Width  = imageInfo.Width;
                        exif.Height = imageInfo.Height;
                    }
                }
                catch { /* ignore */ }
            }
            else if (IsVideoFile(extension))
            {
                try
                {
                    var mediaInfo = await FFmpeg.GetMediaInfo(filePath, cancellationToken);
                    var videoStream = mediaInfo.VideoStreams.FirstOrDefault();
                    if (videoStream != null)
                    {
                        exif.Width  = videoStream.Width;
                        exif.Height = videoStream.Height;
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[DEBUG] Video dimension extraction failed for {filePath}: {ex.Message}");
                }
            }

            return exif;
        }
        catch
        {
            return null;
        }
    }

    // ── Directory extraction ──────────────────────────────────────────────────

    private void ExtractDirectoryMetadata(
        MetadataExtractor.Directory directory,
        AssetExif exif,
        ExtractionOptions options,
        TimeZoneInfo tzInfo)
    {
        if (directory is ExifSubIfdDirectory exifDir)
        {
            if (options.DateTime)
            {
                var raw = exifDir.GetDescription(ExifDirectoryBase.TagDateTimeOriginal);
                if (!string.IsNullOrEmpty(raw) &&
                    System.DateTime.TryParseExact(raw, "yyyy:MM:dd HH:mm:ss", null,
                        System.Globalization.DateTimeStyles.None, out var dt))
                {
                    // Treat the EXIF date as being in the configured timezone, then convert to UTC
                    exif.DateTimeOriginal = System.TimeZoneInfo.ConvertTimeToUtc(
                        System.DateTime.SpecifyKind(dt, DateTimeKind.Unspecified), tzInfo);
                }
            }

            if (options.CameraInfo)
            {
                exif.CameraMake  = exifDir.GetDescription(ExifDirectoryBase.TagMake);
                exif.CameraModel = exifDir.GetDescription(ExifDirectoryBase.TagModel);

                var iso = exifDir.GetDescription(ExifDirectoryBase.TagIsoEquivalent);
                if (!string.IsNullOrEmpty(iso) && int.TryParse(iso, out var isoVal))
                    exif.Iso = isoVal;

                var aperture = exifDir.GetDescription(ExifDirectoryBase.TagAperture);
                if (!string.IsNullOrEmpty(aperture) && double.TryParse(aperture, out var apVal))
                    exif.Aperture = apVal;

                var shutter = exifDir.GetDescription(ExifDirectoryBase.TagShutterSpeed);
                if (!string.IsNullOrEmpty(shutter) && double.TryParse(shutter, out var ssVal))
                    exif.ShutterSpeed = ssVal;

                var focal = exifDir.GetDescription(ExifDirectoryBase.TagFocalLength);
                if (!string.IsNullOrEmpty(focal) && double.TryParse(focal, out var flVal))
                    exif.FocalLength = flVal;

                var orientation = exifDir.GetDescription(ExifDirectoryBase.TagOrientation);
                if (!string.IsNullOrEmpty(orientation) && int.TryParse(orientation, out var orVal))
                    exif.Orientation = orVal;
            }
        }
        else if (directory is GpsDirectory gpsDir && options.Gps)
        {
            if (gpsDir.TryGetGeoLocation(out var location))
            {
                exif.Latitude  = location.Latitude;
                exif.Longitude = location.Longitude;
            }

            var altitude = gpsDir.GetDescription(GpsDirectory.TagAltitude);
            if (!string.IsNullOrEmpty(altitude) && double.TryParse(altitude, out var altVal))
                exif.Altitude = altVal;
        }
        else if (directory is IptcDirectory iptcDir && options.Iptc)
        {
            var caption = iptcDir.GetDescription(IptcDirectory.TagCaption);
            if (!string.IsNullOrEmpty(caption))
            {
                exif.Description = caption.Length > 500 ? caption[..500] : caption;
            }

            var keywords = iptcDir.GetDescription(IptcDirectory.TagKeywords);
            if (!string.IsNullOrEmpty(keywords))
            {
                exif.Keywords = keywords.Length > 1000 ? keywords[..1000] : keywords;
            }
        }
        else if (directory is XmpDirectory xmpDir && options.Iptc)
        {
            // XMP description (dc:description) — only fill if IPTC didn't already set it
            if (string.IsNullOrEmpty(exif.Description))
            {
                var xmpMeta = xmpDir.XmpMeta;
                if (xmpMeta != null)
                {
                    try
                    {
                        var desc = xmpMeta.GetPropertyString("http://purl.org/dc/elements/1.1/", "description");
                        if (!string.IsNullOrEmpty(desc))
                            exif.Description = desc.Length > 500 ? desc[..500] : desc;
                    }
                    catch { /* XMP property may not exist */ }
                }
            }
        }
    }

    // ── XMP sidecar ───────────────────────────────────────────────────────────

    private static IEnumerable<MetadataExtractor.Directory> ReadXmpSidecar(string filePath)
    {
        // Conventions: file.jpg.xmp  OR  file.xmp  (same dir, same stem)
        var candidates = new[]
        {
            filePath + ".xmp",
            Path.Combine(Path.GetDirectoryName(filePath) ?? "",
                Path.GetFileNameWithoutExtension(filePath) + ".xmp")
        };

        foreach (var sidecar in candidates.Distinct())
        {
            if (!File.Exists(sidecar)) continue;

            IEnumerable<MetadataExtractor.Directory> dirs;
            try
            {
                dirs = ImageMetadataReader.ReadMetadata(sidecar);
            }
            catch
            {
                continue;
            }

            return dirs;
        }

        return [];
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static TimeZoneInfo ResolveTimezone(string tzId)
    {
        if (string.IsNullOrWhiteSpace(tzId) || tzId.Equals("UTC", StringComparison.OrdinalIgnoreCase))
            return TimeZoneInfo.Utc;

        try { return TimeZoneInfo.FindSystemTimeZoneById(tzId); }
        catch
        {
            // IANA → Windows mapping fallback on Windows hosts
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

    private static bool IsImageFile(string extension)
    {
        var exts = new[] { ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif",
                           ".gif", ".webp", ".heic", ".heif",
                           ".raw", ".cr2", ".cr3", ".nef", ".arw", ".dng", ".orf", ".rw2", ".pef", ".raf", ".srw" };
        return exts.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }
}
