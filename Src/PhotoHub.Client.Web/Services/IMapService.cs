using PhotoHub.Client.Web.Models;

namespace PhotoHub.Client.Web.Services;

public interface IMapService
{
    Task<List<MapCluster>> GetMapClustersAsync(int? zoom = null, double? minLat = null, double? minLng = null, double? maxLat = null, double? maxLng = null);
    Task<List<MapPoint>> GetMapPointsAsync();
}
