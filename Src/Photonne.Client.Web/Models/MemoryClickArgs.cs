namespace Photonne.Client.Web.Models;

public class MemoryClickArgs
{
    public TimelineItem Asset { get; init; } = null!;
    public List<TimelineItem> GroupAssets { get; init; } = [];
}
