using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public class SettingsService : ISettingsService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public SettingsService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
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
            _httpClient.DefaultRequestHeaders.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
        }
        else
        {
            _httpClient.DefaultRequestHeaders.Authorization = null;
        }
    }

    public async Task<string> GetSettingAsync(string key, string defaultValue = "")
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<SettingResponse>($"/api/settings?key={Uri.EscapeDataString(key)}");
            return response?.Value ?? defaultValue;
        }
        catch
        {
            return defaultValue;
        }
    }

    public async Task<bool> SaveSettingAsync(string key, string value)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/settings", new { Key = key, Value = value });
        return response.IsSuccessStatusCode;
    }

    public async Task<string> GetAssetsPathAsync()
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<AssetsPathResponse>("/api/settings/assets-path");
            return response?.Path ?? "";
        }
        catch
        {
            return "";
        }
    }

    private class SettingResponse
    {
        public string Key { get; set; } = string.Empty;
        public string Value { get; set; } = string.Empty;
    }

    private class AssetsPathResponse
    {
        public string Path { get; set; } = string.Empty;
    }
}
