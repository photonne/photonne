using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public sealed class DemoInfoService : IDemoInfoService
{
    private readonly HttpClient _httpClient;
    private DemoInfo? _cached;
    private Task<DemoInfo>? _inFlight;

    public DemoInfoService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public Task<DemoInfo> GetAsync()
    {
        if (_cached is not null)
            return Task.FromResult(_cached);

        // De-duplicate concurrent first-callers (MainLayout + Login may fire together).
        return _inFlight ??= LoadAsync();
    }

    private async Task<DemoInfo> LoadAsync()
    {
        try
        {
            var response = await _httpClient.GetAsync("/api/admin/demo-info");
            if (!response.IsSuccessStatusCode)
            {
                _cached = DemoInfo.Disabled;
                return _cached;
            }

            var payload = await response.Content.ReadFromJsonAsync<DemoInfoDto>();
            _cached = payload is null
                ? DemoInfo.Disabled
                : new DemoInfo(
                    payload.Enabled,
                    payload.DemoUsername,
                    payload.DemoPassword,
                    payload.ResetIntervalHours,
                    payload.NextResetAt);
            return _cached;
        }
        catch
        {
            _cached = DemoInfo.Disabled;
            return _cached;
        }
        finally
        {
            _inFlight = null;
        }
    }

    private sealed record DemoInfoDto(
        bool Enabled,
        string? DemoUsername,
        string? DemoPassword,
        int? ResetIntervalHours,
        DateTimeOffset? NextResetAt);
}
