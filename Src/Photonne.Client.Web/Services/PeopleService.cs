using System.Net.Http.Headers;
using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public class PeopleService : IPeopleService
{
    private readonly HttpClient _http;
    private readonly Func<Task<string?>>? _getToken;

    public PeopleService(HttpClient http, Func<Task<string?>>? getToken = null)
    {
        _http = http;
        _getToken = getToken;
    }

    private async Task SetAuthAsync()
    {
        if (_getToken == null) return;
        var token = await _getToken();
        _http.DefaultRequestHeaders.Authorization = string.IsNullOrEmpty(token)
            ? null
            : new AuthenticationHeaderValue("Bearer", token);
    }

    public async Task<PeoplePage> GetPeopleAsync(int limit = 50, int offset = 0, bool includeHidden = false, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var url = $"/api/people?limit={limit}&offset={offset}&includeHidden={(includeHidden ? "true" : "false")}";
        var page = await _http.GetFromJsonAsync<PeoplePage>(url, ct);
        return page ?? new PeoplePage(0, new List<PersonSummary>());
    }

    public async Task RenameAsync(Guid personId, string? name, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PatchAsJsonAsync($"/api/people/{personId}", new { name }, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task HideAsync(Guid personId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/people/{personId}/hide", null, ct);
        resp.EnsureSuccessStatusCode();
    }
}
