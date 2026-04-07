using System.Net.Http.Json;
using System.Net.Http.Headers;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class FolderService : IFolderService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public FolderService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
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

    public async Task<List<FolderItem>> GetFoldersAsync()
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<List<FolderItem>>("/api/folders");
            return response ?? new List<FolderItem>();
        }
        catch
        {
            return new List<FolderItem>();
        }
    }

    public async Task<FolderItem?> GetFolderByIdAsync(Guid id)
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<FolderItem>($"/api/folders/{id}");
            return response;
        }
        catch
        {
            return null;
        }
    }

    public async Task<List<FolderItem>> GetFolderTreeAsync()
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<List<FolderItem>>("/api/folders/tree");
            return response ?? new List<FolderItem>();
        }
        catch
        {
            return new List<FolderItem>();
        }
    }

    public async Task<List<FolderItem>> GetMyFolderTreeAsync()
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<List<FolderItem>>("/api/utilities/folders/tree");
            return response ?? new List<FolderItem>();
        }
        catch
        {
            return new List<FolderItem>();
        }
    }

    public async Task<List<TimelineItem>> GetFolderAssetsAsync(Guid folderId)
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<List<TimelineItem>>($"/api/folders/{folderId}/assets");
            return response ?? new List<TimelineItem>();
        }
        catch
        {
            return new List<TimelineItem>();
        }
    }

    public async Task<FolderItem> CreateFolderAsync(CreateFolderRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/folders", request);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<FolderItem>() ?? throw new Exception("Failed to create folder");
    }

    public async Task<FolderItem> UpdateFolderAsync(Guid folderId, UpdateFolderRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PutAsJsonAsync($"/api/folders/{folderId}", request);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<FolderItem>() ?? throw new Exception("Failed to update folder");
    }

    public async Task DeleteFolderAsync(Guid folderId)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.DeleteAsync($"/api/folders/{folderId}");
        response.EnsureSuccessStatusCode();
    }

    public async Task MoveFolderAssetsAsync(MoveFolderAssetsRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/folders/assets/move", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task RemoveFolderAssetsAsync(RemoveFolderAssetsRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/folders/assets/remove", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task<FolderItem?> GetLibraryRootFolderAsync(Guid libraryId)
    {
        try
        {
            await SetAuthHeaderAsync();
            return await _httpClient.GetFromJsonAsync<FolderItem?>($"/api/folders/library/{libraryId}/root");
        }
        catch
        {
            return null;
        }
    }
}
