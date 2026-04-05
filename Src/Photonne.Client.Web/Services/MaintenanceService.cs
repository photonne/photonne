using System.Net.Http.Headers;
using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class MaintenanceService : IMaintenanceService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public MaintenanceService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient = httpClient;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthHeaderAsync()
    {
        if (_getTokenFunc == null) return;
        var token = await _getTokenFunc();
        _httpClient.DefaultRequestHeaders.Authorization = !string.IsNullOrEmpty(token)
            ? new AuthenticationHeaderValue("Bearer", token)
            : null;
    }

    public async Task<MaintenanceTaskResult> CleanOrphanThumbnailsAsync(CancellationToken ct = default)
        => await PostAsync("orphan-thumbnails", ct);

    public async Task<MaintenanceTaskResult> MarkMissingFilesAsync(CancellationToken ct = default)
        => await PostAsync("missing-files", ct);

    public async Task<MaintenanceTaskResult> RecalculateSizesAsync(CancellationToken ct = default)
        => await PostAsync("recalculate-sizes", ct);

    public async Task<MaintenanceTaskResult> EmptyGlobalTrashAsync(CancellationToken ct = default)
        => await PostAsync("empty-trash", ct);

    private async Task<MaintenanceTaskResult> PostAsync(string task, CancellationToken ct)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsync($"/api/admin/maintenance/{task}", null, ct);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<MaintenanceTaskResult>(ct)
               ?? new MaintenanceTaskResult { Success = false, Message = "Sin respuesta del servidor." };
    }
}
