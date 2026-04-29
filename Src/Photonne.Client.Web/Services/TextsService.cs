using System.Net.Http.Headers;
using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public class TextsService : ITextsService
{
    private readonly HttpClient _http;
    private readonly Func<Task<string?>>? _getToken;

    public TextsService(HttpClient http, Func<Task<string?>>? getToken = null)
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

    public async Task<List<RecognizedTextLineItem>> GetTextForAssetAsync(Guid assetId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var items = await _http.GetFromJsonAsync<List<RecognizedTextLineItem>>(
            $"/api/assets/{assetId}/text", ct);
        return items ?? new();
    }
}
