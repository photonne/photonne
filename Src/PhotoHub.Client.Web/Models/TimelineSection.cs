namespace PhotoHub.Client.Web.Models;

public class TimelineSection
{
    public string YearMonth { get; set; } = "";
    public DateTime StartDate { get; set; }
    public DateTime EndDate { get; set; }   // exclusive upper bound
    public int EstimatedCount { get; set; }
    public int EstimatedHeight { get; set; }
    public bool IsLoaded { get; set; }
    public bool IsLoading { get; set; }
    public bool IsMasonryDone { get; set; }
    public List<TimelineItem> Items { get; set; } = new();
    public string SectionId => $"section-{YearMonth}";
}
