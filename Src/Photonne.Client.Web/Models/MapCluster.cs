using Photonne.Client.Web;

namespace Photonne.Client.Web.Models;

public class MapCluster
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
    
    public string ThumbnailUrl => FirstAssetId.HasValue && HasThumbnail
        ? $"{ApiConfig.BaseUrl}/api/assets/{FirstAssetId.Value}/thumbnail?size=Medium"
        : string.Empty;

    public override string ToString()
    {
        return $"Cluster: {Latitude:F4}, {Longitude:F4}, Count: {Count}, Date: {EarliestDate:yyyy-MM-dd}";
    }
}
