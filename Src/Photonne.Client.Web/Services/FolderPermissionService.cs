using System.Net;
using System.Net.Http.Json;
using System.Net.Http.Headers;

namespace Photonne.Client.Web.Services;

public class FolderPermissionService : IFolderPermissionService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public FolderPermissionService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
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

    public async Task<List<FolderPermissionDto>> GetFolderPermissionsAsync(Guid folderId)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetAsync($"/api/folders/{folderId}/permissions");
        ThrowIfForbidden(response);
        if (!response.IsSuccessStatusCode)
        {
            return new List<FolderPermissionDto>();
        }

        return await response.Content.ReadFromJsonAsync<List<FolderPermissionDto>>() ?? new List<FolderPermissionDto>();
    }

    public async Task<FolderPermissionDto> SetFolderPermissionAsync(Guid folderId, SetFolderPermissionRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync($"/api/folders/{folderId}/permissions", request);
        ThrowIfForbidden(response);
        if (!response.IsSuccessStatusCode)
        {
            throw new Exception("No se pudo asignar permisos a la carpeta.");
        }

        return await response.Content.ReadFromJsonAsync<FolderPermissionDto>()
            ?? throw new Exception("Failed to set permission");
    }

    public async Task DeleteFolderPermissionAsync(Guid folderId, Guid userId)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.DeleteAsync($"/api/folders/{folderId}/permissions/{userId}");
        ThrowIfForbidden(response);
        response.EnsureSuccessStatusCode();
    }
}
