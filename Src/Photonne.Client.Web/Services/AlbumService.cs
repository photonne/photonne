using System.Net;
using System.Net.Http.Json;
using System.Net.Http.Headers;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class AlbumService : IAlbumService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public AlbumService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
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

    public async Task<List<AlbumItem>> GetAlbumsAsync()
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetFromJsonAsync<List<AlbumItem>>("/api/albums");
            return response ?? new List<AlbumItem>();
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return new List<AlbumItem>();
        }
    }

    public async Task<AlbumItem?> GetAlbumByIdAsync(Guid id)
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetAsync($"/api/albums/{id}");
            ThrowIfForbidden(response);
            if (!response.IsSuccessStatusCode)
            {
                return null;
            }

            return await response.Content.ReadFromJsonAsync<AlbumItem>();
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return null;
        }
    }

    public async Task<List<TimelineItem>> GetAlbumAssetsAsync(Guid albumId)
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.GetAsync($"/api/albums/{albumId}/assets");
            ThrowIfForbidden(response);
            if (!response.IsSuccessStatusCode)
            {
                return new List<TimelineItem>();
            }

            return await response.Content.ReadFromJsonAsync<List<TimelineItem>>() ?? new List<TimelineItem>();
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return new List<TimelineItem>();
        }
    }

    public async Task<AlbumItem?> CreateAlbumAsync(string name, string? description)
    {
        try
        {
            await SetAuthHeaderAsync();
            var request = new { Name = name, Description = description };
            var response = await _httpClient.PostAsJsonAsync("/api/albums", request);
            
            if (response.IsSuccessStatusCode)
            {
                return await response.Content.ReadFromJsonAsync<AlbumItem>();
            }
            
            return null;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return null;
        }
    }

    public async Task<bool> UpdateAlbumAsync(Guid id, string name, string? description)
    {
        try
        {
            await SetAuthHeaderAsync();
            var request = new { Name = name, Description = description };
            var response = await _httpClient.PutAsJsonAsync($"/api/albums/{id}", request);
            ThrowIfForbidden(response);
            return response.IsSuccessStatusCode;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> DeleteAlbumAsync(Guid id)
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.DeleteAsync($"/api/albums/{id}");
            ThrowIfForbidden(response);
            return response.IsSuccessStatusCode;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> LeaveAlbumAsync(Guid albumId)
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.PostAsync($"/api/albums/{albumId}/leave", null);
            ThrowIfForbidden(response);
            return response.IsSuccessStatusCode;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> AddAssetToAlbumAsync(Guid albumId, Guid assetId)
    {
        try
        {
            await SetAuthHeaderAsync();
            var request = new { AssetId = assetId };
            var response = await _httpClient.PostAsJsonAsync($"/api/albums/{albumId}/assets", request);
            ThrowIfForbidden(response);
            if (response.IsSuccessStatusCode)
            {
                return true;
            }

            var message = NormalizeAlbumErrorMessage(await TryReadApiErrorMessageAsync(response));
            if (!string.IsNullOrWhiteSpace(message))
            {
                throw new InvalidOperationException(message);
            }

            return false;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> AddAssetsToAlbumAsync(Guid albumId, IEnumerable<Guid> assetIds)
    {
        try
        {
            await SetAuthHeaderAsync();
            var request = new { AssetIds = assetIds.ToList() };
            var response = await _httpClient.PostAsJsonAsync($"/api/albums/{albumId}/assets/batch", request);
            ThrowIfForbidden(response);
            return response.IsSuccessStatusCode;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> RemoveAssetFromAlbumAsync(Guid albumId, Guid assetId)
    {
        try
        {
            await SetAuthHeaderAsync();
            var response = await _httpClient.DeleteAsync($"/api/albums/{albumId}/assets/{assetId}");
            ThrowIfForbidden(response);
            return response.IsSuccessStatusCode;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    public async Task<bool> SetAlbumCoverAsync(Guid albumId, Guid assetId)
    {
        try
        {
            await SetAuthHeaderAsync();
            var request = new { AssetId = assetId };
            var response = await _httpClient.PutAsJsonAsync($"/api/albums/{albumId}/cover", request);
            ThrowIfForbidden(response);
            return response.IsSuccessStatusCode;
        }
        catch (UnauthorizedAccessException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    private static async Task<string?> TryReadApiErrorMessageAsync(HttpResponseMessage response)
    {
        if (response.Content == null)
        {
            return null;
        }

        var content = await response.Content.ReadAsStringAsync();
        if (string.IsNullOrWhiteSpace(content))
        {
            return null;
        }

        try
        {
            using var document = JsonDocument.Parse(content);
            if (document.RootElement.ValueKind != JsonValueKind.Object)
            {
                return content.Trim();
            }

            var message = TryGetString(document.RootElement, "error")
                ?? TryGetString(document.RootElement, "message")
                ?? TryGetString(document.RootElement, "detail")
                ?? TryGetString(document.RootElement, "title");

            return message ?? content.Trim();
        }
        catch
        {
            return content.Trim();
        }
    }

    private static string? TryGetString(JsonElement element, string propertyName)
    {
        if (element.TryGetProperty(propertyName, out var value) &&
            value.ValueKind == JsonValueKind.String)
        {
            return value.GetString();
        }

        return null;
    }

    private static string? NormalizeAlbumErrorMessage(string? message)
    {
        if (string.IsNullOrWhiteSpace(message))
        {
            return null;
        }

        var trimmed = message.Trim();
        if (trimmed.Contains("already in this album", StringComparison.OrdinalIgnoreCase))
        {
            return "El elemento ya está en el álbum.";
        }

        return trimmed;
    }
}
