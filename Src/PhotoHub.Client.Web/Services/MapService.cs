using System.Net.Http.Json;
using PhotoHub.Client.Web.Models;

namespace PhotoHub.Client.Web.Services;

public class MapService : IMapService
{
    private readonly HttpClient _httpClient;

    public MapService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<List<MapCluster>> GetMapClustersAsync(int? zoom = null, double? minLat = null, double? minLng = null, double? maxLat = null, double? maxLng = null)
    {
        var queryParams = new List<string>();
        
        if (zoom.HasValue)
            queryParams.Add($"zoom={zoom.Value}");
        if (minLat.HasValue)
            queryParams.Add($"minLat={minLat.Value}");
        if (minLng.HasValue)
            queryParams.Add($"minLng={minLng.Value}");
        if (maxLat.HasValue)
            queryParams.Add($"maxLat={maxLat.Value}");
        if (maxLng.HasValue)
            queryParams.Add($"maxLng={maxLng.Value}");

        var queryString = queryParams.Any() ? "?" + string.Join("&", queryParams) : "";
        var response = await _httpClient.GetFromJsonAsync<List<MapCluster>>($"/api/assets/map{queryString}");

        return response ?? new List<MapCluster>();
    }

    public async Task<List<MapPoint>> GetMapPointsAsync()
    {
        var response = await _httpClient.GetFromJsonAsync<List<MapPoint>>("/api/assets/map/points");
        return response ?? new List<MapPoint>();
    }
}
