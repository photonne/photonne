using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using MetadataExtractor;
using MetadataExtractor.Formats.Exif;
using MetadataExtractor.Formats.Iptc;
using MetadataExtractor.Formats.QuickTime;
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

            // Fallback EXIF date tags when DateTimeOriginal (0x9003) is absent:
            // scanners write DateTimeDigitized, and editors/exporters often keep
            // only the IFD0 DateTime ("modify date"). Finder/Preview read these
            // too, so to the user "the file has EXIF" while we saw no date.
            // Priority: Digitized (capture-adjacent) over IFD0 DateTime.
            if (exif.DateTimeOriginal == null && options.DateTime)
            {
                exif.DateTimeOriginal =
                    ParseExifDateTag(directories, ExifDirectoryBase.TagDateTimeDigitized, tzInfo)
                    ?? ParseExifDateTag(directories, ExifDirectoryBase.TagDateTime, tzInfo);
            }

            // Android MP4s carry GPS as an ISO 6709 string in the legacy
            // moov/udta/©xyz atom, which MetadataExtractor doesn't surface
            // (the keys/ilst branch above only covers Apple-style MOVs) —
            // scan for it manually when the library pass found no GPS.
            if (IsVideoFile(extension) && options.Gps &&
                exif.Latitude == null && exif.Longitude == null)
            {
                var xyz = await Task.Run(() => TryReadUdtaXyzLocation(filePath), cancellationToken);
                if (xyz != null &&
                    TryParseIso6709(xyz, out var xyzLat, out var xyzLon, out var xyzAlt) &&
                    (Math.Abs(xyzLat) > 0.0001 || Math.Abs(xyzLon) > 0.0001))
                {
                    exif.Latitude = xyzLat;
                    exif.Longitude = xyzLon;
                    if (xyzAlt.HasValue) exif.Altitude = xyzAlt;
                }
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
        // GpsDirectory extends ExifDirectoryBase, so it MUST be matched first —
        // otherwise it falls into the ExifDirectoryBase branch below, which then
        // overwrites CameraMake/Model with nulls (GPS dir has no TagMake) and
        // skips GPS extraction entirely. Check type first, options second, so a
        // GPS directory never falls through to the generic EXIF handler.
        if (directory is GpsDirectory gpsDir)
        {
            if (!options.Gps) return;

            if (gpsDir.TryGetGeoLocation(out var location))
            {
                // Descartar coordenadas (0,0) — "Null Island": cámaras/exportadores
                // a veces escriben el tag GPS a cero cuando no hay fix real.
                if (Math.Abs(location.Latitude) > 0.0001 || Math.Abs(location.Longitude) > 0.0001)
                {
                    exif.Latitude  = location.Latitude;
                    exif.Longitude = location.Longitude;
                }
            }

            var altitude = gpsDir.GetDescription(GpsDirectory.TagAltitude);
            if (!string.IsNullOrEmpty(altitude) && double.TryParse(altitude, out var altVal))
                exif.Altitude = altVal;

            return;
        }

        // ExifDirectoryBase covers both IFD0 (ExifIfd0Directory, where Make/Model
        // live per EXIF spec) and the Exif SubIFD (ExifSubIfdDirectory, where
        // DateTimeOriginal and exposure tags live). Matching only SubIFD silently
        // dropped Make/Model from every photo.
        if (directory is ExifDirectoryBase exifDir)
        {
            if (options.DateTime)
            {
                var raw = exifDir.GetDescription(ExifDirectoryBase.TagDateTimeOriginal);
                if (!string.IsNullOrEmpty(raw) &&
                    System.DateTime.TryParseExact(raw, "yyyy:MM:dd HH:mm:ss", null,
                        System.Globalization.DateTimeStyles.None, out var dt))
                {
                    // EXIF DateTimeOriginal is the local wall-clock the camera
                    // recorded (no zone). Store it AS-IS so "capture date" and
                    // on-this-day stay in the photo's own local frame instead of
                    // shifting with a configured timezone.
                    exif.DateTimeOriginal = System.DateTime.SpecifyKind(dt, DateTimeKind.Unspecified);
                }
            }

            if (options.CameraInfo)
            {
                // Make/Model/Orientation live in IFD0; ISO/exposure/focal live in SubIFD.
                // Both directories hit this branch (both derive from ExifDirectoryBase),
                // so we must NOT overwrite an already-populated field with a null read
                // from the other directory.
                var make = exifDir.GetDescription(ExifDirectoryBase.TagMake);
                if (!string.IsNullOrEmpty(make)) exif.CameraMake = make;

                var model = exifDir.GetDescription(ExifDirectoryBase.TagModel);
                if (!string.IsNullOrEmpty(model)) exif.CameraModel = model;

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

                // GetDescription returns the human label ("Top, left side (Horizontal / normal)"),
                // not a number. Orientation is a uint16 — read the raw value directly.
                if (exifDir.TryGetInt32(ExifDirectoryBase.TagOrientation, out var orVal))
                    exif.Orientation = orVal;
            }
        }
        else if (directory is QuickTimeMovieHeaderDirectory qtMovieDir && options.DateTime)
        {
            // Videos (MP4/MOV/M4V/3GP): creation time from the QuickTime
            // "mvhd" atom, which is UTC per spec — no timezone conversion.
            // Previously videos never got a DateTimeOriginal, so their
            // CapturedAt fell back to the filesystem date and they piled up
            // on whatever day the files were last copied. Guard against the
            // 1904/1970 epoch placeholders cameras write when the clock was
            // never set, and never overwrite an EXIF-provided date.
            if (exif.DateTimeOriginal == null &&
                qtMovieDir.TryGetDateTime(QuickTimeMovieHeaderDirectory.TagCreated, out var qtCreated) &&
                qtCreated.Year > 1970)
            {
                // mvhd creation time is genuine UTC — convert it INTO the
                // configured local timezone so videos land in the same
                // wall-clock frame as photos' EXIF dates (which are stored as-is).
                exif.DateTimeOriginal = MetadataTimeZone.ToLocalWallClock(qtCreated, tzInfo);
            }
        }
        else if (directory is QuickTimeMetadataHeaderDirectory qtMetaDir && options.Gps)
        {
            // Videos (MOV/MP4 from iPhone/Android): GPS lives in the QuickTime
            // metadata atom as an ISO 6709 string ("+41.3874+002.1686+020.000/"),
            // not in an EXIF GpsDirectory — without this branch no video ever
            // got coordinates. Never clobber EXIF-provided GPS.
            if (exif.Latitude == null && exif.Longitude == null)
            {
                var iso6709 = qtMetaDir.GetDescription(QuickTimeMetadataHeaderDirectory.TagGpsLocation);
                if (!string.IsNullOrEmpty(iso6709) &&
                    TryParseIso6709(iso6709, out var lat, out var lon, out var alt))
                {
                    // Same Null Island guard as the EXIF GpsDirectory branch.
                    if (Math.Abs(lat) > 0.0001 || Math.Abs(lon) > 0.0001)
                    {
                        exif.Latitude = lat;
                        exif.Longitude = lon;
                        if (alt.HasValue) exif.Altitude = alt;
                    }
                }
            }
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

    /// <summary>
    /// Parses an ISO 6709 location string as written by QuickTime metadata
    /// ("+34.0679-118.4438+010.123/" — lat, lon, optional altitude; each
    /// field starts with an explicit sign, '/' terminates). Returns false
    /// when fewer than two coordinates are present or parsing fails.
    /// </summary>
    internal static bool TryParseIso6709(string value, out double latitude, out double longitude, out double? altitude)
    {
        latitude = 0;
        longitude = 0;
        altitude = null;

        var matches = System.Text.RegularExpressions.Regex.Matches(value, @"[+-]\d+(?:\.\d+)?");
        if (matches.Count < 2) return false;

        if (!double.TryParse(matches[0].Value, System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out latitude))
            return false;
        if (!double.TryParse(matches[1].Value, System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out longitude))
            return false;
        if (Math.Abs(latitude) > 90 || Math.Abs(longitude) > 180) return false;

        if (matches.Count >= 3 &&
            double.TryParse(matches[2].Value, System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out var alt))
        {
            altitude = alt;
        }

        return true;
    }

    /// <summary>
    /// Scans every EXIF directory for <paramref name="tag"/> and returns the
    /// first parseable "yyyy:MM:dd HH:mm:ss" value converted from the
    /// configured timezone to UTC. Mirrors the DateTimeOriginal handling in
    /// <see cref="ExtractDirectoryMetadata"/>.
    /// </summary>
    private static System.DateTime? ParseExifDateTag(
        IEnumerable<MetadataExtractor.Directory> directories,
        int tag,
        TimeZoneInfo tzInfo)
    {
        foreach (var directory in directories)
        {
            if (directory is not ExifDirectoryBase exifDir) continue;

            var raw = exifDir.GetDescription(tag);
            if (!string.IsNullOrEmpty(raw) &&
                System.DateTime.TryParseExact(raw, "yyyy:MM:dd HH:mm:ss", null,
                    System.Globalization.DateTimeStyles.None, out var dt))
            {
                // Local wall-clock, stored as-is (same convention as DateTimeOriginal).
                return System.DateTime.SpecifyKind(dt, DateTimeKind.Unspecified);
            }
        }
        return null;
    }

    /// <summary>
    /// Reads the ISO 6709 location string from the legacy MP4
    /// moov/udta/©xyz atom (2-byte length + 2-byte language code + text),
    /// the place Android camera apps write GPS. Returns null when the atom
    /// is absent or the file isn't parseable as ISO BMFF.
    /// </summary>
    internal static string? TryReadUdtaXyzLocation(string filePath)
    {
        try
        {
            using var stream = File.OpenRead(filePath);
            var moov = FindBox(stream, 0, stream.Length, "moov");
            if (moov == null) return null;
            var udta = FindBox(stream, moov.Value.Start, moov.Value.End, "udta");
            if (udta == null) return null;
            var xyz = FindBox(stream, udta.Value.Start, udta.Value.End, "©xyz");
            if (xyz == null) return null;

            var payloadLength = (int)Math.Min(xyz.Value.End - xyz.Value.Start, 4096);
            if (payloadLength < 4) return null;
            stream.Position = xyz.Value.Start;
            var payload = new byte[payloadLength];
            stream.ReadExactly(payload);

            // Big-endian text length, then 2-byte language code, then the string.
            var textLength = (payload[0] << 8) | payload[1];
            if (textLength <= 0 || textLength > payloadLength - 4) textLength = payloadLength - 4;
            return System.Text.Encoding.UTF8.GetString(payload, 4, textLength);
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// Walks sibling ISO BMFF boxes in [start, end) and returns the payload
    /// range of the first box whose 4CC matches <paramref name="type"/>.
    /// </summary>
    private static (long Start, long End)? FindBox(Stream stream, long start, long end, string type)
    {
        Span<byte> header = stackalloc byte[8];
        var target = System.Text.Encoding.Latin1.GetBytes(type);
        var position = start;
        while (position + 8 <= end)
        {
            stream.Position = position;
            if (stream.Read(header) < 8) return null;
            long size = (uint)((header[0] << 24) | (header[1] << 16) | (header[2] << 8) | header[3]);
            var headerSize = 8L;
            if (size == 1)
            {
                Span<byte> large = stackalloc byte[8];
                if (stream.Read(large) < 8) return null;
                size = System.Buffers.Binary.BinaryPrimitives.ReadInt64BigEndian(large);
                headerSize = 16;
            }
            else if (size == 0)
            {
                size = end - position;
            }
            if (size < headerSize) return null;

            if (header[4] == target[0] && header[5] == target[1] &&
                header[6] == target[2] && header[7] == target[3])
            {
                return (position + headerSize, Math.Min(position + size, end));
            }
            position += size;
        }
        return null;
    }

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
