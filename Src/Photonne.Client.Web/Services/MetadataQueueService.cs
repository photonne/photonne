using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class MetadataQueueService : IMetadataQueueService
{
    private readonly HttpClient _httpClient;
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public MetadataQueueService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public IAsyncEnumerable<MetadataProgressUpdate> ExtractMetadataAsync(
        bool overwriteAll = false,
        CancellationToken cancellationToken = default)
    {
        var url = $"/api/assets/metadata/stream?overwrite={overwriteAll}";
        return _httpClient.GetFromJsonAsAsyncEnumerable<MetadataProgressUpdate>(url, _jsonOptions, cancellationToken)!;
    }
}
