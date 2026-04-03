using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IMapService
{
    Task<List<MapCluster>> GetMapClustersAsync(int? zoom = null, double? minLat = null, double? minLng = null, double? maxLat = null, double? maxLng = null);
    Task<List<MapPoint>> GetMapPointsAsync();
}
