using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class ThumbnailQueueService : IThumbnailQueueService
{
    private readonly HttpClient _httpClient;
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public ThumbnailQueueService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public IAsyncEnumerable<ThumbnailProgressUpdate> GenerateThumbnailsAsync(
        bool regenerateAll = false,
        CancellationToken cancellationToken = default)
    {
        var url = $"/api/assets/thumbnails/stream?regenerate={regenerateAll}";
        return _httpClient.GetFromJsonAsAsyncEnumerable<ThumbnailProgressUpdate>(url, _jsonOptions, cancellationToken)!;
    }
}
