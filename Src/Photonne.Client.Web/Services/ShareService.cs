using System.Net.Http.Headers;
using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class ShareService : IShareService
{
    private readonly HttpClient _http;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public ShareService(HttpClient http, Func<Task<string?>>? getTokenFunc = null)
    {
        _http = http;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthAsync()
    {
        string? token = _getTokenFunc != null ? await _getTokenFunc() : null;
        _http.DefaultRequestHeaders.Authorization = !string.IsNullOrEmpty(token)
            ? new AuthenticationHeaderValue("Bearer", token)
            : null;
    }

    // ── Public links ──────────────────────────────────────────────────────────

    public async Task<CreateShareLinkResponse?> CreatePublicShareAsync(CreateShareLinkRequest request)
    {
        await SetAuthAsync();
        var response = await _http.PostAsJsonAsync("/api/share", request);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<CreateShareLinkResponse>();
    }

    public async Task<List<CreateShareLinkResponse>> GetShareLinksAsync(Guid? assetId, Guid? albumId)
    {
        await SetAuthAsync();
        var qs = assetId.HasValue ? $"assetId={assetId}" : $"albumId={albumId}";
        var response = await _http.GetFromJsonAsync<List<CreateShareLinkResponse>>($"/api/share?{qs}");
        return response ?? new();
    }

    public async Task RevokeShareAsync(string token)
    {
        await SetAuthAsync();
        var response = await _http.DeleteAsync($"/api/share/{token}");
        response.EnsureSuccessStatusCode();
    }

    // ── Public page ───────────────────────────────────────────────────────────

    public async Task<SharedContentResponse?> GetSharedContentAsync(string token, string? password = null)
    {
        var url = string.IsNullOrEmpty(password)
            ? $"/api/share/{token}"
            : $"/api/share/{token}?pw={Uri.EscapeDataString(password)}";
        return await _http.GetFromJsonAsync<SharedContentResponse>(url);
    }

    // ── Sent links ────────────────────────────────────────────────────────────

    public async Task<List<SentShareLinkDto>> GetSentShareLinksAsync()
    {
        await SetAuthAsync();
        var result = await _http.GetFromJsonAsync<List<SentShareLinkDto>>("/api/share/sent");
        return result ?? new();
    }

    public async Task<UpdateShareLinkResponse?> UpdateShareAsync(string token, UpdateShareLinkRequest request)
    {
        await SetAuthAsync();
        var response = await _http.PatchAsJsonAsync($"/api/share/{token}", request);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<UpdateShareLinkResponse>();
    }
}
