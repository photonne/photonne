namespace Photonne.Server.Api.Features.Map;

public class MapClusterResponse
{
    public string Id { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public int Count { get; set; }
    public List<Guid> AssetIds { get; set; } = new();
    public DateTime EarliestDate { get; set; }
    public DateTime LatestDate { get; set; }
    public Guid? FirstAssetId { get; set; }
    public bool HasThumbnail { get; set; }
}
