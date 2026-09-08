using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IAlbumsService
{
    Task<List<AlbumSummary>> GetAlbumsAsync(CancellationToken cancellationToken = default);
    Task AddAssetsAsync(Guid albumId, IReadOnlyCollection<Guid> assetIds);
    Task<AlbumSummary?> CreateAlbumAsync(string name);
}

/// <summary>Lo mínimo de álbumes que usa el workspace; el CRUD completo es fase C.</summary>
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

    public async Task AddAssetsAsync(Guid albumId, IReadOnlyCollection<Guid> assetIds)
    {
        var response = await _httpClient.PostAsJsonAsync(
            $"/api/albums/{albumId}/assets/batch", new { assetIds });
        response.EnsureSuccessStatusCode();
    }

    public async Task<AlbumSummary?> CreateAlbumAsync(string name)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/albums", new { name, description = (string?)null });
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<AlbumSummary>();
    }
}
