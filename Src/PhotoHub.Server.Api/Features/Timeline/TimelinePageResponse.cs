namespace PhotoHub.Server.Api.Features.Timeline;

public class TimelinePageResponse
{
    public List<TimelineResponse> Items { get; set; } = new();
    public bool HasMore { get; set; }

    /// <summary>
    /// CreatedDate of the oldest item returned. Pass as the <c>cursor</c> query parameter
    /// to fetch the next page (exclusive upper bound on CreatedDate).
    /// Null when there are no more pages.
    /// </summary>
    public DateTime? NextCursor { get; set; }
}
