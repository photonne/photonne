using System.Net;
using System.Net.Http.Json;
using System.Net.Http.Headers;

namespace Photonne.Client.Web.Services;

public class AlbumPermissionService : IAlbumPermissionService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public AlbumPermissionService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
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

    private static void ThrowIfForbidden(HttpResponseMessage response)
    {
        if (response.StatusCode == HttpStatusCode.Forbidden ||
            response.StatusCode == HttpStatusCode.Unauthorized)
        {
            throw new UnauthorizedAccessException("No tienes permisos suficientes para realizar esta acción.");
        }
    }

    public async Task<List<AlbumPermissionDto>> GetAlbumPermissionsAsync(Guid albumId)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetAsync($"/api/albums/{albumId}/permissions");
        ThrowIfForbidden(response);
        if (!response.IsSuccessStatusCode)
        {
            return new List<AlbumPermissionDto>();
        }

        return await response.Content.ReadFromJsonAsync<List<AlbumPermissionDto>>() ?? new List<AlbumPermissionDto>();
    }

    public async Task<AlbumPermissionDto> SetAlbumPermissionAsync(Guid albumId, SetAlbumPermissionRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync($"/api/albums/{albumId}/permissions", request);
        ThrowIfForbidden(response);
        if (!response.IsSuccessStatusCode)
        {
            throw new Exception("No se pudo asignar permisos al álbum.");
        }

        return await response.Content.ReadFromJsonAsync<AlbumPermissionDto>()
            ?? throw new Exception("Failed to set permission");
    }

    public async Task DeleteAlbumPermissionAsync(Guid albumId, Guid userId)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.DeleteAsync($"/api/albums/{albumId}/permissions/{userId}");
        ThrowIfForbidden(response);
        response.EnsureSuccessStatusCode();
    }
}
