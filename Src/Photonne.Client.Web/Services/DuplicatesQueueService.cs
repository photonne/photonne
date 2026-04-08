using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class DuplicatesQueueService : IDuplicatesQueueService
{
    private readonly HttpClient _httpClient;
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public DuplicatesQueueService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public IAsyncEnumerable<DuplicatesProgressUpdate> DetectDuplicatesAsync(
        bool cleanup = false,
        bool physical = false,
        CancellationToken cancellationToken = default)
    {
        var url = $"/api/assets/duplicates/stream?cleanup={cleanup}&physical={physical}";
        return _httpClient.GetFromJsonAsAsyncEnumerable<DuplicatesProgressUpdate>(url, _jsonOptions, cancellationToken)!;
    }

    public async Task<PhysicalDeleteResult> DeletePhysicalFilesAsync(
        List<PhysicalFileDeleteRequest> files,
        CancellationToken cancellationToken = default)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/assets/duplicates/physical/delete", files, cancellationToken);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<PhysicalDeleteResult>(_jsonOptions, cancellationToken)
               ?? new PhysicalDeleteResult();
    }
}
