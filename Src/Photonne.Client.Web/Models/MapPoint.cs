using Photonne.Client.Web;

namespace Photonne.Client.Web.Models;

/// <summary>
/// Punto geolocalizado de /api/assets/map/points. ThumbnailUrl se serializa
/// también hacia JS (camelCase): los marcadores de mapHelpers leen
/// options.thumbnailUrl para pintar la miniatura circular.
/// </summary>
public class MapPoint
{
    public Guid Id { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public bool HasThumbnail { get; set; }
    public DateTime? Date { get; set; }

    public string? ThumbnailUrl => HasThumbnail
        ? $"{ApiConfig.BaseUrl}/api/assets/{Id}/thumbnail?size=Small"
        : null;
}
