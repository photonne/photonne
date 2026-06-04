using System.Globalization;
using System.Text.RegularExpressions;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Infers a capture date for assets whose EXIF has no DateTimeOriginal, from
/// the file name (camera/WhatsApp naming) or the folder path (date-organized
/// libraries like <c>.../2010/2010-08-15 Vacaciones/foto.jpg</c>). Used by the
/// date-restore task; never applied over real EXIF or manual dates (the
/// caller gates on <see cref="Models.CaptureDateSource"/>).
///
/// The pure parsing core is static so it can be unit-tested without DI; the
/// instance wrapper resolves the configured timezone (the parsed values are
/// local wall-clock times, same convention as <see cref="ExifExtractorService"/>)
/// and returns UTC.
/// </summary>
public class CaptureDateInferenceService
{
    private readonly SettingsService _settingsService;

    public CaptureDateInferenceService(SettingsService settingsService)
    {
        _settingsService = settingsService;
    }

    /// <summary>Where an inferred date came from — surfaced in task messages.</summary>
    public enum InferenceOrigin { FileName, FolderPath }

    public sealed record InferenceResult(DateTime DateUtc, InferenceOrigin Origin);

    /// <summary>
    /// Tries to infer the capture date from <paramref name="fileName"/> first
    /// (most precise — usually carries a full timestamp), then from the
    /// virtual <paramref name="fullPath"/> folder segments. Returns null when
    /// nothing parseable/valid is found.
    /// </summary>
    public async Task<InferenceResult?> TryInferAsync(string fileName, string fullPath, CancellationToken ct)
    {
        var tzId = await _settingsService.GetSettingAsync(
            ExifExtractorService.KeyDefaultTimezone, Guid.Empty, "UTC");
        var tz = ResolveTimezone(tzId);

        var local = TryInferLocal(fileName, fullPath, out var origin);
        if (local == null) return null;

        var utc = TimeZoneInfo.ConvertTimeToUtc(
            DateTime.SpecifyKind(local.Value, DateTimeKind.Unspecified), tz);
        return new InferenceResult(utc, origin);
    }

    // ── Pure parsing core (unit-testable) ────────────────────────────────────

    // Priority: filename full timestamp > filename date-only > folder
    // "yyyy-MM-dd Nombre" segment (deepest first) > numeric /yyyy/MM(/dd)/
    // hierarchy > bare /yyyy/ segment.
    public static DateTime? TryInferLocal(string fileName, string fullPath, out InferenceOrigin origin)
    {
        origin = InferenceOrigin.FileName;

        var fromName = TryParseFileName(fileName);
        if (fromName != null) return fromName;

        origin = InferenceOrigin.FolderPath;
        return TryParsePathSegments(fullPath, fileName);
    }

    // "IMG_20100815_123456.jpg", "VID-20100815-123456.mp4", "PXL_20100815_123456789.jpg"
    // (Pixel appends milliseconds — tolerated and discarded),
    // "Screenshot_20100815-123456.png", "20100815_123456.jpg"
    private static readonly Regex FullTimestamp = new(
        @"(?<!\d)((?:19|20)\d{2})(\d{2})(\d{2})[_\- .]?(\d{2})(\d{2})(\d{2})(?:\d{3})?(?!\d)",
        RegexOptions.Compiled);

    // "2010-08-15 12.34.56", "2010-08-15_12-34-56" (Dropbox/Signal style)
    private static readonly Regex DashedTimestamp = new(
        @"(?<!\d)((?:19|20)\d{2})-(\d{2})-(\d{2})[ _\-](\d{2})[\.\-:](\d{2})[\.\-:](\d{2})(?!\d)",
        RegexOptions.Compiled);

    // WhatsApp "IMG-20100815-WA0001.jpg" / "VID-20100815-WA0001.mp4" and any
    // other bare 8-digit date in the name → noon.
    private static readonly Regex DateOnly8 = new(
        @"(?<!\d)((?:19|20)\d{2})(\d{2})(\d{2})(?!\d)",
        RegexOptions.Compiled);

    // "2010-08-15 Vacaciones" folder-segment prefix (also bare "2010-08-15").
    private static readonly Regex SegmentDatePrefix = new(
        @"^((?:19|20)\d{2})-(\d{2})-(\d{2})(?:\b|_)",
        RegexOptions.Compiled);

    private static DateTime? TryParseFileName(string fileName)
    {
        var stem = Path.GetFileNameWithoutExtension(fileName);

        var m = FullTimestamp.Match(stem);
        if (m.Success && TryBuild(m, withTime: true, out var dt)) return dt;

        m = DashedTimestamp.Match(stem);
        if (m.Success && TryBuild(m, withTime: true, out dt)) return dt;

        m = DateOnly8.Match(stem);
        if (m.Success && TryBuild(m, withTime: false, out dt)) return dt;

        return null;
    }

    private static DateTime? TryParsePathSegments(string fullPath, string fileName)
    {
        var segments = fullPath.Replace('\\', '/')
            .Split('/', StringSplitOptions.RemoveEmptyEntries)
            .ToList();
        // Drop the filename itself — only folders carry path-level dates.
        if (segments.Count > 0 &&
            string.Equals(segments[^1], fileName, StringComparison.OrdinalIgnoreCase))
        {
            segments.RemoveAt(segments.Count - 1);
        }

        // 1. "yyyy-MM-dd Nombre" prefix, deepest segment first (most specific).
        for (var i = segments.Count - 1; i >= 0; i--)
        {
            var m = SegmentDatePrefix.Match(segments[i]);
            if (m.Success && TryBuild(m, withTime: false, out var dt)) return dt;
        }

        // 2. Numeric hierarchy /yyyy/MM(/dd)/ — scan for a year segment whose
        //    children are a valid month (and optionally day).
        for (var i = 0; i < segments.Count; i++)
        {
            if (!IsValidYearSegment(segments[i], out var year)) continue;

            if (i + 1 < segments.Count &&
                int.TryParse(segments[i + 1], NumberStyles.None, CultureInfo.InvariantCulture, out var month) &&
                month is >= 1 and <= 12)
            {
                var day = 1;
                if (i + 2 < segments.Count &&
                    int.TryParse(segments[i + 2], NumberStyles.None, CultureInfo.InvariantCulture, out var d) &&
                    d >= 1 && d <= DateTime.DaysInMonth(year, month))
                {
                    day = d;
                }
                return new DateTime(year, month, day, 12, 0, 0);
            }
        }

        // 3. Bare year folder — last resort: Jan 1st, noon.
        for (var i = segments.Count - 1; i >= 0; i--)
        {
            if (IsValidYearSegment(segments[i], out var year))
                return new DateTime(year, 1, 1, 12, 0, 0);
        }

        return null;
    }

    private static bool IsValidYearSegment(string segment, out int year)
    {
        year = 0;
        return segment.Length == 4
            && int.TryParse(segment, NumberStyles.None, CultureInfo.InvariantCulture, out year)
            && IsPlausibleYear(year);
    }

    private static bool TryBuild(Match m, bool withTime, out DateTime result)
    {
        result = default;
        var year = int.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture);
        var month = int.Parse(m.Groups[2].Value, CultureInfo.InvariantCulture);
        var day = int.Parse(m.Groups[3].Value, CultureInfo.InvariantCulture);

        if (!IsPlausibleYear(year)) return false;
        if (month is < 1 or > 12) return false;
        if (day < 1 || day > DateTime.DaysInMonth(year, month)) return false;

        int hour = 12, minute = 0, second = 0;
        if (withTime)
        {
            hour = int.Parse(m.Groups[4].Value, CultureInfo.InvariantCulture);
            minute = int.Parse(m.Groups[5].Value, CultureInfo.InvariantCulture);
            second = int.Parse(m.Groups[6].Value, CultureInfo.InvariantCulture);
            if (hour > 23 || minute > 59 || second > 59) return false;
        }

        result = new DateTime(year, month, day, hour, minute, second);
        return true;
    }

    // Digital-photo era sanity window; rejects phone numbers, resolutions and
    // other 8-digit noise that happens to slice into a "date".
    private static bool IsPlausibleYear(int year) =>
        year >= 1980 && year <= DateTime.UtcNow.Year + 1;

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
}
