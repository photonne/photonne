using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IAlbumsService
{
    Task<List<AlbumSummary>> GetAlbumsAsync(CancellationToken cancellationToken = default);
    Task<AlbumSummary?> GetAlbumAsync(Guid albumId);
    Task<List<TimelineItem>> GetAlbumAssetsAsync(Guid albumId);
    Task AddAssetsAsync(Guid albumId, IReadOnlyCollection<Guid> assetIds);
    Task RemoveAssetAsync(Guid albumId, Guid assetId);
    Task<AlbumSummary?> CreateAlbumAsync(string name, string? description = null, SmartRuleNode? smartRule = null);
    Task UpdateAlbumAsync(Guid albumId, string name, string? description);
    Task DeleteAlbumAsync(Guid albumId);
    Task LeaveAlbumAsync(Guid albumId);
    Task SetCoverAsync(Guid albumId, Guid assetId);
    Task<SmartRulePreview?> PreviewRuleAsync(SmartRuleNode rule, int sampleSize = 24);
}

public class SmartRulePreview
{
    public int Count { get; set; }
    public List<Guid> SampleAssetIds { get; set; } = new();
}

/// <summary>Álbumes del workspace: listado, gestión y creación (manual o smart).</summary>
public class AlbumsService : IAlbumsService
{
    private readonly HttpClient _httpClient;

    public AlbumsService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<List<AlbumSummary>> GetAlbumsAsync(CancellationToken cancellationToken = default)
    {
        var response = await _httpClient.GetFromJsonAsync<List<AlbumSummary>>("/api/albums", cancellationToken);
        return response ?? new List<AlbumSummary>();
    }

    public Task<AlbumSummary?> GetAlbumAsync(Guid albumId) =>
        _httpClient.GetFromJsonAsync<AlbumSummary>($"/api/albums/{albumId}");

    public async Task<List<TimelineItem>> GetAlbumAssetsAsync(Guid albumId)
    {
        var response = await _httpClient.GetFromJsonAsync<List<TimelineItem>>($"/api/albums/{albumId}/assets");
        return response ?? new List<TimelineItem>();
    }

    public async Task AddAssetsAsync(Guid albumId, IReadOnlyCollection<Guid> assetIds)
    {
        var response = await _httpClient.PostAsJsonAsync(
            $"/api/albums/{albumId}/assets/batch", new { assetIds });
        response.EnsureSuccessStatusCode();
    }

    public async Task RemoveAssetAsync(Guid albumId, Guid assetId)
    {
        var response = await _httpClient.DeleteAsync($"/api/albums/{albumId}/assets/{assetId}");
        response.EnsureSuccessStatusCode();
    }

    public async Task<AlbumSummary?> CreateAlbumAsync(string name, string? description = null, SmartRuleNode? smartRule = null)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/albums", new { name, description, smartRule }, SmartRuleNode.JsonOptions);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<AlbumSummary>();
    }

    public async Task UpdateAlbumAsync(Guid albumId, string name, string? description)
    {
        var response = await _httpClient.PutAsJsonAsync(
            $"/api/albums/{albumId}", new { name, description });
        response.EnsureSuccessStatusCode();
    }

    public async Task DeleteAlbumAsync(Guid albumId)
    {
        var response = await _httpClient.DeleteAsync($"/api/albums/{albumId}");
        response.EnsureSuccessStatusCode();
    }

    public async Task LeaveAlbumAsync(Guid albumId)
    {
        var response = await _httpClient.PostAsync($"/api/albums/{albumId}/leave", null);
        response.EnsureSuccessStatusCode();
    }

    public async Task SetCoverAsync(Guid albumId, Guid assetId)
    {
        var response = await _httpClient.PutAsJsonAsync(
            $"/api/albums/{albumId}/cover", new { assetId });
        response.EnsureSuccessStatusCode();
    }

    public async Task<SmartRulePreview?> PreviewRuleAsync(SmartRuleNode rule, int sampleSize = 24)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/albums/preview", new { rule, sampleSize }, SmartRuleNode.JsonOptions);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<SmartRulePreview>();
    }
}
