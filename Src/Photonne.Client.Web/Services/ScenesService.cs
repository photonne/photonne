using System.Net.Http.Headers;
using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public class ScenesService : IScenesService
{
    private readonly HttpClient _http;
    private readonly Func<Task<string?>>? _getToken;

    public ScenesService(HttpClient http, Func<Task<string?>>? getToken = null)
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

    public async Task<List<ClassifiedSceneItem>> GetScenesForAssetAsync(Guid assetId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var items = await _http.GetFromJsonAsync<List<ClassifiedSceneItem>>(
            $"/api/assets/{assetId}/scenes", ct);
        return items ?? new();
    }

    public async Task<List<SceneLabelItem>> GetLabelsAsync(string? search = null, int limit = 200, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var qs = new List<string> { $"limit={limit}" };
        if (!string.IsNullOrWhiteSpace(search)) qs.Add($"q={Uri.EscapeDataString(search)}");
        var items = await _http.GetFromJsonAsync<List<SceneLabelItem>>(
            "/api/scenes/labels?" + string.Join("&", qs), ct);
        return items ?? new();
    }
}
