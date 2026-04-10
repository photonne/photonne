namespace Photonne.Client.Web.Models;

public class TimelineNeighborsResult
{
    public List<TimelineNeighborItem> Items { get; set; } = new();
    public int CurrentIndex { get; set; }
    public bool HasMoreBefore { get; set; }
    public bool HasMoreAfter { get; set; }
}

public class TimelineNeighborItem
{
    public Guid Id { get; set; }
    public string FullPath { get; set; } = string.Empty;
    public DateTime FileCreatedAt { get; set; }
}
