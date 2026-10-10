namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// One calendar month of the timeline skeleton: how many visible assets the
/// requesting user has in that month. The full bucket list is small (a few
/// bytes per month of library history), so the client fetches it in one
/// request and reserves scroll height for every month before any asset data
/// arrives — see docs/timeline-buckets.md.
/// </summary>
public class TimelineBucketResponse
{
    /// <summary>Calendar month key, "yyyy-MM" (e.g. "2026-06").</summary>
    public string Key { get; set; } = "";

    /// <summary>
    /// Number of visible assets whose CapturedAt falls in that month. Must
    /// always equal the item count returned by /api/assets/timeline/buckets/{key}
    /// — clients reserve skeleton heights from it.
    /// </summary>
    public int Count { get; set; }
}
