using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class IndexService : IIndexService
{
    private readonly HttpClient _httpClient;
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public IndexService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public IAsyncEnumerable<IndexProgressUpdate> IndexDirectoryAsync(
        CancellationToken cancellationToken = default)
    {
        var url = "/api/assets/index/stream";
        return _httpClient.GetFromJsonAsAsyncEnumerable<IndexProgressUpdate>(url, _jsonOptions, cancellationToken)!;
    }

    public IAsyncEnumerable<IndexProgressUpdate> ResumeAsync(
        Guid taskId,
        CancellationToken cancellationToken = default)
    {
        return _httpClient.GetFromJsonAsAsyncEnumerable<IndexProgressUpdate>(
            $"/api/tasks/{taskId}/stream", _jsonOptions, cancellationToken)!;
    }
}
