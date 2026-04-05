using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class ExternalLibraryPermissionService : IExternalLibraryPermissionService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public ExternalLibraryPermissionService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient = httpClient;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthHeaderAsync()
    {
        if (_getTokenFunc == null) return;
        var token = await _getTokenFunc();
        _httpClient.DefaultRequestHeaders.Authorization = string.IsNullOrEmpty(token)
            ? null
            : new AuthenticationHeaderValue("Bearer", token);
    }

    public async Task<List<ExternalLibraryPermissionDto>> GetPermissionsAsync(Guid libraryId, CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        return await _httpClient.GetFromJsonAsync<List<ExternalLibraryPermissionDto>>(
                   $"/api/libraries/{libraryId}/permissions", _jsonOptions, ct)
               ?? new List<ExternalLibraryPermissionDto>();
    }

    public async Task<ExternalLibraryPermissionDto?> SetPermissionAsync(Guid libraryId, SetExternalLibraryPermissionRequest request, CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync($"/api/libraries/{libraryId}/permissions", request, ct);
        if (!response.IsSuccessStatusCode) return null;
        return await response.Content.ReadFromJsonAsync<ExternalLibraryPermissionDto>(_jsonOptions, ct);
    }

    public async Task<bool> RemovePermissionAsync(Guid libraryId, Guid userId, CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.DeleteAsync($"/api/libraries/{libraryId}/permissions/{userId}", ct);
        return response.IsSuccessStatusCode;
    }
}
