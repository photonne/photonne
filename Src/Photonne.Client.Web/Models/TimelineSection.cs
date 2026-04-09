namespace Photonne.Client.Web.Models;

public class TimelineSection
{
    public string YearMonth { get; set; } = "";
    public DateTime StartDate { get; set; }
    public DateTime EndDate { get; set; }   // exclusive upper bound
    public int EstimatedCount { get; set; }
    public List<TimelineGridItem> GridItems { get; set; } = new();
    public bool IsMasonryDone { get; set; }
    public string SectionId => $"section-{YearMonth}";
}
