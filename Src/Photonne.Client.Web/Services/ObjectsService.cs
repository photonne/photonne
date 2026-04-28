using System.Net.Http.Headers;
using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public class ObjectsService : IObjectsService
{
    private readonly HttpClient _http;
    private readonly Func<Task<string?>>? _getToken;

    public ObjectsService(HttpClient http, Func<Task<string?>>? getToken = null)
    {
        _http = http;
        _getToken = getToken;
    }

    private async Task SetAuthAsync()
    {
        if (_getToken == null) return;
        var token = await _getToken();
        _http.DefaultRequestHeaders.Authorization = string.IsNullOrEmpty(token)
            ? null
            : new AuthenticationHeaderValue("Bearer", token);
    }

    public async Task<List<DetectedObjectItem>> GetObjectsForAssetAsync(Guid assetId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var items = await _http.GetFromJsonAsync<List<DetectedObjectItem>>(
            $"/api/assets/{assetId}/objects", ct);
        return items ?? new();
    }

    public async Task<List<ObjectLabelItem>> GetLabelsAsync(string? search = null, int limit = 200, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var qs = new List<string> { $"limit={limit}" };
        if (!string.IsNullOrWhiteSpace(search)) qs.Add($"q={Uri.EscapeDataString(search)}");
        var items = await _http.GetFromJsonAsync<List<ObjectLabelItem>>(
            "/api/objects/labels?" + string.Join("&", qs), ct);
        return items ?? new();
    }
}
