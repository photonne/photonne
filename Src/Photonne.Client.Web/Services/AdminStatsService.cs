using System.Net.Http.Headers;
using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class AdminStatsService : IAdminStatsService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public AdminStatsService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient = httpClient;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthHeaderAsync()
    {
        string? token = null;
        if (_getTokenFunc != null)
        {
            token = await _getTokenFunc();
        }

        if (!string.IsNullOrEmpty(token))
        {
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        else
        {
            _httpClient.DefaultRequestHeaders.Authorization = null;
        }
    }

    public async Task<AdminStatsResponse> GetStatsAsync()
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetFromJsonAsync<AdminStatsResponse>("/api/admin/stats");
        return response ?? new AdminStatsResponse();
    }
}
