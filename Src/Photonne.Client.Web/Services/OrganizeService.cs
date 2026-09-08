using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class OrganizeCount
{
    public int Count { get; set; }
    public DateTime? Oldest { get; set; }
    public DateTime? Newest { get; set; }
}

/// <summary>Lote propuesto por el servidor (viaje/persona/escena/mes) con sus ids completos.</summary>
public class OrganizeSuggestion
{
    public string Kind { get; set; } = string.Empty;
    public string Key { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string? From { get; set; }
    public string? To { get; set; }
    public int Count { get; set; }
    public Guid? CoverAssetId { get; set; }
    public List<Guid> AssetIds { get; set; } = new();

    public string KindLabel => Kind switch
    {
        "trip" => "Viaje",
        "person" => "Persona",
        "scene" => "Escena",
        "month" => "Mes",
        _ => Kind
    };
}

public class OrganizeRulePreview
{
    public int Count { get; set; }
    public List<Guid> SampleAssetIds { get; set; } = new();
    public List<OrganizeYearCount> YearBreakdown { get; set; } = new();
}

public class OrganizeYearCount
{
    public int Year { get; set; }
    public int Count { get; set; }
}

public interface IOrganizeService
{
    Task<OrganizeCount> GetCountAsync();
    Task<TimelinePageResult> GetInboxPageAsync(DateTime? cursor = null, int pageSize = 150);
    Task<List<OrganizeSuggestion>> GetSuggestionsAsync();
    Task<int> SetExcludedAsync(IReadOnlyCollection<Guid> assetIds, bool excluded);
    Task<OrganizeRulePreview?> PreviewRuleAsync(SmartRuleNode rule, int sampleSize = 24);
    Task<List<YearBreakdownGroup>> ReviewRuleAsync(SmartRuleNode rule);
}

/// <summary>
/// Bandeja "Para organizar": lo subido por backup que aún no vive en una
/// carpeta. Los movimientos reutilizan /api/folders/assets/move.
/// </summary>
public class OrganizeService : IOrganizeService
{
    private readonly HttpClient _httpClient;

    public OrganizeService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<OrganizeCount> GetCountAsync()
    {
        var response = await _httpClient.GetFromJsonAsync<OrganizeCount>("/api/organize/inbox/count");
        return response ?? new OrganizeCount();
    }

    public async Task<TimelinePageResult> GetInboxPageAsync(DateTime? cursor = null, int pageSize = 150)
    {
        var url = $"/api/organize/inbox?pageSize={pageSize}";
        if (cursor.HasValue)
            url += $"&cursor={Uri.EscapeDataString(cursor.Value.ToUniversalTime().ToString("o"))}";
        var response = await _httpClient.GetFromJsonAsync<TimelinePageResult>(url);
        return response ?? new TimelinePageResult();
    }

    public async Task<List<OrganizeSuggestion>> GetSuggestionsAsync()
    {
        var response = await _httpClient.GetFromJsonAsync<List<OrganizeSuggestion>>("/api/organize/suggestions");
        return response ?? new List<OrganizeSuggestion>();
    }

    public async Task<int> SetExcludedAsync(IReadOnlyCollection<Guid> assetIds, bool excluded)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/organize/exclude", new { assetIds, excluded });
        response.EnsureSuccessStatusCode();
        var payload = await response.Content.ReadFromJsonAsync<ExcludeResponse>();
        return payload?.Updated ?? 0;
    }

    public async Task<OrganizeRulePreview?> PreviewRuleAsync(SmartRuleNode rule, int sampleSize = 24)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/organize/rule/preview", new { rule, sampleSize }, SmartRuleNode.JsonOptions);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<OrganizeRulePreview>();
    }

    public async Task<List<YearBreakdownGroup>> ReviewRuleAsync(SmartRuleNode rule)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/organize/rule/review", new { rule }, SmartRuleNode.JsonOptions);
        response.EnsureSuccessStatusCode();
        var payload = await response.Content.ReadFromJsonAsync<ReviewResponse>();
        return payload?.Groups ?? new List<YearBreakdownGroup>();
    }

    private sealed class ExcludeResponse
    {
        public int Updated { get; set; }
    }

    private sealed class ReviewResponse
    {
        public List<YearBreakdownGroup> Groups { get; set; } = new();
    }
}
