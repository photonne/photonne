namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Per-item layout data returned by the grid endpoint.
/// Contains everything needed to render correctly-proportioned tiles with dominant-color
/// placeholders. Thumbnails are loaded lazily by the client using the Id.
/// </summary>
public class TimelineGridItemResponse
{
    public Guid Id { get; set; }
    public string Type { get; set; } = "Image"; // "Image" or "Video"
    public double AspectRatio { get; set; } = 1.0;
    public string Date { get; set; } = "";      // "yyyy-MM-dd" for day grouping
    public string? DominantColor { get; set; }  // "#rrggbb" from Small thumbnail
    public int Width { get; set; }
    public int Height { get; set; }
    public bool IsReadOnly { get; set; }
}

/// <summary>
/// All items for a single calendar month.
/// </summary>
public class TimelineGridSectionResponse
{
    public string YearMonth { get; set; } = "";  // "yyyy-MM"
    public List<TimelineGridItemResponse> Items { get; set; } = new();
}
