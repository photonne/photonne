namespace Photonne.Client.Web.Models;

/// <summary>
/// Per-item layout data used to render the full timeline grid with correct proportions
/// and dominant-color placeholders. Thumbnails are loaded lazily using the Id.
/// </summary>
public class TimelineGridItem
{
    public Guid Id { get; set; }
    public string Type { get; set; } = "Image"; // "Image" or "Video"
    public double AspectRatio { get; set; } = 1.0;
    public string Date { get; set; } = "";       // "yyyy-MM-dd"
    public string? DominantColor { get; set; }   // "#rrggbb"
    public int Width { get; set; }
    public int Height { get; set; }

    public string ThumbnailUrl => $"{ApiConfig.BaseUrl}/api/assets/{Id}/thumbnail?size=Small";
}

public class TimelineGridSection
{
    public string YearMonth { get; set; } = "";
    public List<TimelineGridItem> Items { get; set; } = new();
}
