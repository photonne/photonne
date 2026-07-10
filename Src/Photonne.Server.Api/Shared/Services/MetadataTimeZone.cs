namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Single source of truth for the configured <c>MetadataSettings.DefaultTimezone</c>
/// and the conversions the app needs now that capture dates are stored as the
/// photo's LOCAL wall-clock (timezone-naive), not UTC.
///
/// Convention: EXIF DateTimeOriginal is already local wall-clock and is stored
/// as-is. Only genuinely-absolute sources get converted INTO this timezone:
/// filesystem timestamps, the QuickTime <c>mvhd</c> creation time (UTC per
/// spec), and the server clock when computing the user's "today" for
/// on-this-day. Replaces the four private <c>ResolveTimezone</c> copies that
/// used to live in ExifExtractor / ExifWriter / CaptureDateInference /
/// NightlyScheduler.
/// </summary>
public static class MetadataTimeZone
{
    /// <summary>Resolves the configured timezone from settings (defaults to UTC).</summary>
    public static async Task<TimeZoneInfo> ResolveAsync(SettingsService settings, CancellationToken ct = default)
        => Resolve(await settings.GetSettingAsync(ExifExtractorService.KeyDefaultTimezone, Guid.Empty, "UTC"));

    /// <summary>
    /// Resolves an IANA timezone id, tolerating a Windows host (IANA→Windows
    /// mapping) and falling back to UTC for blank/unknown ids.
    /// </summary>
    public static TimeZoneInfo Resolve(string? tzId)
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

    /// <summary>
    /// Turns a genuinely-UTC instant into local wall-clock (naive,
    /// <see cref="DateTimeKind.Unspecified"/>) so it lands in the same frame as
    /// stored EXIF dates. DST-aware. A no-op when the zone is UTC.
    /// </summary>
    public static DateTime ToLocalWallClock(DateTime utc, TimeZoneInfo tz)
        => DateTime.SpecifyKind(
               TimeZoneInfo.ConvertTimeFromUtc(DateTime.SpecifyKind(utc, DateTimeKind.Utc), tz),
               DateTimeKind.Unspecified);

    /// <summary>
    /// The user's local "now" as a naive wall-clock value, matching the frame
    /// of stored capture dates — used for on-this-day day/month/year comparisons.
    /// </summary>
    public static DateTime LocalNow(TimeZoneInfo tz)
        => DateTime.SpecifyKind(
               TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz),
               DateTimeKind.Unspecified);
}
