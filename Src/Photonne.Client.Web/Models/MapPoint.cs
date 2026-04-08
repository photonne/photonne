namespace Photonne.Client.Web.Models;

public class MapPoint
{
    public Guid Id { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public bool HasThumbnail { get; set; }
    public DateTime Date { get; set; }

    public string ThumbnailUrl => HasThumbnail
        ? $"{ApiConfig.BaseUrl}/api/assets/{Id}/thumbnail?size=Small"
        : string.Empty;
}
