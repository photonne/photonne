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

    public async Task<PeoplePage> GetPeopleAsync(
        int limit = 50,
        int offset = 0,
        bool includeHidden = false,
        string? search = null,
        string? sort = null,
        string? sortDir = null,
        bool unnamedFirst = false,
        CancellationToken ct = default)
    {
        await SetAuthAsync();
        var qs = new List<string>
        {
            $"limit={limit}",
            $"offset={offset}",
            $"includeHidden={(includeHidden ? "true" : "false")}",
        };
        if (!string.IsNullOrWhiteSpace(search)) qs.Add($"search={Uri.EscapeDataString(search)}");
        if (!string.IsNullOrWhiteSpace(sort)) qs.Add($"sort={sort}");
        if (!string.IsNullOrWhiteSpace(sortDir)) qs.Add($"sortDir={sortDir}");
        if (unnamedFirst) qs.Add("unnamedFirst=true");
        var url = "/api/people?" + string.Join("&", qs);
        var page = await _http.GetFromJsonAsync<PeoplePage>(url, ct);
        return page ?? new PeoplePage(0, new List<PersonSummary>());
    }

    public async Task<PersonSummary?> GetPersonAsync(Guid id, CancellationToken ct = default)
    {
        await SetAuthAsync();
        try
        {
            return await _http.GetFromJsonAsync<PersonSummary>($"/api/people/{id}", ct);
        }
        catch (HttpRequestException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task<PersonAssetsPage> GetPersonAssetsAsync(Guid id, int limit = 100, int offset = 0, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var url = $"/api/search/people/{id}/assets?limit={limit}&offset={offset}";
        var page = await _http.GetFromJsonAsync<PersonAssetsPage>(url, ct);
        return page ?? new PersonAssetsPage(0, new List<PersonAssetItem>());
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

    public async Task UnhideAsync(Guid personId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/people/{personId}/unhide", null, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task<int> ReclusterAsync(CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync("/api/people/recluster", null, ct);
        resp.EnsureSuccessStatusCode();
        var dto = await resp.Content.ReadFromJsonAsync<ReclusterDto>(cancellationToken: ct);
        return dto?.PersonsCreated ?? 0;
    }

    private sealed record ReclusterDto(int PersonsCreated);

    public async Task<int> UnlinkAssetFromPersonAsync(Guid personId, Guid assetId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/people/{personId}/assets/{assetId}/unlink", null, ct);
        resp.EnsureSuccessStatusCode();
        var dto = await resp.Content.ReadFromJsonAsync<UnlinkDto>(cancellationToken: ct);
        return dto?.FacesDetached ?? 0;
    }

    private sealed record UnlinkDto(int FacesDetached);

    public async Task MergeAsync(Guid targetId, Guid sourceId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/people/{targetId}/merge/{sourceId}", null, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task<PersonFacesPage> GetPersonFacesAsync(Guid personId, int limit = 60, int offset = 0, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var url = $"/api/people/{personId}/faces?limit={limit}&offset={offset}";
        var page = await _http.GetFromJsonAsync<PersonFacesPage>(url, ct);
        return page ?? new PersonFacesPage(0, new List<PersonFaceItem>());
    }

    public async Task SetCoverFaceAsync(Guid personId, Guid faceId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/people/{personId}/cover/{faceId}", null, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task<List<FaceItem>> GetFacesForAssetAsync(Guid assetId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var faces = await _http.GetFromJsonAsync<List<FaceItem>>($"/api/assets/{assetId}/faces", ct);
        return faces ?? new List<FaceItem>();
    }

    public async Task AssignFaceAsync(Guid faceId, Guid? personId, string? newPersonName, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var body = new { personId, newPersonName };
        var resp = await _http.PostAsJsonAsync($"/api/faces/{faceId}/assign", body, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task UnassignFaceAsync(Guid faceId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/faces/{faceId}/unassign", null, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task RejectFaceAsync(Guid faceId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.DeleteAsync($"/api/faces/{faceId}", ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task<PersonSuggestionsPage> GetPersonSuggestionsAsync(Guid personId, int limit = 30, int offset = 0, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var url = $"/api/people/{personId}/suggestions?limit={limit}&offset={offset}";
        var page = await _http.GetFromJsonAsync<PersonSuggestionsPage>(url, ct);
        return page ?? new PersonSuggestionsPage(0, new List<PersonSuggestionItem>());
    }

    public async Task AcceptFaceSuggestionAsync(Guid faceId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/faces/{faceId}/accept-suggestion", null, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task DismissFaceSuggestionAsync(Guid faceId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/faces/{faceId}/dismiss-suggestion", null, ct);
        resp.EnsureSuccessStatusCode();
    }

    public async Task<int> AcceptAllSuggestionsAsync(Guid personId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/people/{personId}/suggestions/accept-all", null, ct);
        resp.EnsureSuccessStatusCode();
        var dto = await resp.Content.ReadFromJsonAsync<BulkResult>(cancellationToken: ct);
        return dto?.Affected ?? 0;
    }

    public async Task<int> DismissAllSuggestionsAsync(Guid personId, CancellationToken ct = default)
    {
        await SetAuthAsync();
        var resp = await _http.PostAsync($"/api/people/{personId}/suggestions/dismiss-all", null, ct);
        resp.EnsureSuccessStatusCode();
        var dto = await resp.Content.ReadFromJsonAsync<BulkResult>(cancellationToken: ct);
        return dto?.Affected ?? 0;
    }

    private sealed record BulkResult(int Affected);
}
