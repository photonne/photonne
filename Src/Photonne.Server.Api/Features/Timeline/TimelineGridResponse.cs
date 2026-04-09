namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Lightweight per-item data returned by the grid endpoint.
/// Only type, date, and aspect ratio — no URLs, checksums, or heavy metadata.
/// </summary>
public class TimelineGridItemResponse
{
    public string Type { get; set; } = "Image"; // "Image" or "Video"
    public double AspectRatio { get; set; } = 1.0;
    public string Date { get; set; } = "";      // "yyyy-MM-dd" for day grouping in the skeleton
}

/// <summary>
/// All items for a single calendar month.
/// </summary>
public class TimelineGridSectionResponse
{
    public string YearMonth { get; set; } = "";  // "yyyy-MM"
    public List<TimelineGridItemResponse> Items { get; set; } = new();
}
