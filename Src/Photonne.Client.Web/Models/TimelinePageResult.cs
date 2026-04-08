namespace Photonne.Client.Web.Models;

public class TimelinePageResult
{
    public List<TimelineItem> Items { get; set; } = new();
    public bool HasMore { get; set; }
    public DateTime? NextCursor { get; set; }
}
