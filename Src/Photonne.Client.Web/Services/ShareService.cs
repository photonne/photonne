using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class ShareService : IShareService
{
    private readonly HttpClient _http;

    public ShareService(HttpClient http)
    {
        _http = http;
    }

    public async Task<SharedContentResponse?> GetSharedContentAsync(string token, string? password = null)
    {
        var url = string.IsNullOrEmpty(password)
            ? $"/api/share/{token}"
            : $"/api/share/{token}?pw={Uri.EscapeDataString(password)}";
        return await _http.GetFromJsonAsync<SharedContentResponse>(url);
    }
}
