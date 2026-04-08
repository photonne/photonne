using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class ExternalLibraryService : IExternalLibraryService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public ExternalLibraryService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient = httpClient;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthHeaderAsync()
    {
        if (_getTokenFunc == null) return;
        var token = await _getTokenFunc();
        if (!string.IsNullOrEmpty(token))
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
    }

    public async Task<List<ExternalLibraryDto>> GetAllAsync(CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        return await _httpClient.GetFromJsonAsync<List<ExternalLibraryDto>>("/api/libraries", _jsonOptions, ct)
               ?? new List<ExternalLibraryDto>();
    }

    public async Task<ExternalLibraryDto?> GetByIdAsync(Guid id, CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        return await _httpClient.GetFromJsonAsync<ExternalLibraryDto>($"/api/libraries/{id}", _jsonOptions, ct);
    }

    public async Task<ExternalLibraryDto?> CreateAsync(CreateExternalLibraryRequest request, CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/libraries", request, ct);
        if (!response.IsSuccessStatusCode) return null;
        return await response.Content.ReadFromJsonAsync<ExternalLibraryDto>(_jsonOptions, ct);
    }

    public async Task<bool> UpdateAsync(Guid id, UpdateExternalLibraryRequest request, CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PutAsJsonAsync($"/api/libraries/{id}", request, ct);
        return response.IsSuccessStatusCode;
    }

    public async Task<bool> DeleteAsync(Guid id, CancellationToken ct = default)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.DeleteAsync($"/api/libraries/{id}", ct);
        return response.IsSuccessStatusCode;
    }

    public IAsyncEnumerable<LibraryScanProgressUpdate> ScanAsync(Guid id, CancellationToken ct = default)
    {
        // Auth header is already set by the shared HttpClient handler (AuthRefreshHandler)
        return _httpClient.GetFromJsonAsAsyncEnumerable<LibraryScanProgressUpdate>(
            $"/api/libraries/{id}/scan/stream", _jsonOptions, ct)!;
    }

    public IAsyncEnumerable<LibraryScanProgressUpdate> ResumeAsync(Guid taskId, CancellationToken ct = default)
    {
        return _httpClient.GetFromJsonAsAsyncEnumerable<LibraryScanProgressUpdate>(
            $"/api/tasks/{taskId}/stream", _jsonOptions, ct)!;
    }
}
