namespace Photonne.Client.Web.Models;

/// <summary>
/// Lightweight per-item data for skeleton rendering before full section data loads.
/// </summary>
public class TimelineGridItem
{
    public string Type { get; set; } = "Image"; // "Image" or "Video"
    public double AspectRatio { get; set; } = 1.0;
    public string Date { get; set; } = "";       // "yyyy-MM-dd"
}

public class TimelineGridSection
{
    public string YearMonth { get; set; } = "";
    public List<TimelineGridItem> Items { get; set; } = new();
}
