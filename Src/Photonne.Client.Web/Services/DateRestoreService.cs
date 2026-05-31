using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class DateRestoreService : IDateRestoreService
{
    private readonly HttpClient _httpClient;
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public DateRestoreService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public IAsyncEnumerable<MetadataProgressUpdate> RestoreDatesAsync(
        bool fromFile = false,
        CancellationToken cancellationToken = default)
    {
        var url = $"/api/assets/dates/restore/stream?fromFile={fromFile}";
        return _httpClient.GetFromJsonAsAsyncEnumerable<MetadataProgressUpdate>(url, _jsonOptions, cancellationToken)!;
    }

    public IAsyncEnumerable<MetadataProgressUpdate> ResumeAsync(
        Guid taskId,
        CancellationToken cancellationToken = default)
    {
        return _httpClient.GetFromJsonAsAsyncEnumerable<MetadataProgressUpdate>(
            $"/api/tasks/{taskId}/stream", _jsonOptions, cancellationToken)!;
    }
}
