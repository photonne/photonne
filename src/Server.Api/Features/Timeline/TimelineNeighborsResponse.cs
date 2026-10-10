namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Lightweight response for asset navigation in AssetDetail.
/// Contains only the fields needed for prev/next navigation, not full asset data.
/// </summary>
public class TimelineNeighborsResponse
{
    public List<TimelineNeighborItem> Items { get; set; } = new();

    /// <summary>Index of the requested asset within <see cref="Items"/>.</summary>
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
