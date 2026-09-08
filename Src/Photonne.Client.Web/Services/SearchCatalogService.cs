using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public class PersonSummary
{
    public Guid Id { get; set; }
    public string? Name { get; set; }
    public Guid? CoverFaceId { get; set; }
    public int FaceCount { get; set; }
    public bool IsHidden { get; set; }

    public string DisplayName => string.IsNullOrWhiteSpace(Name) ? "Sin nombre" : Name!;
}

public class LabelSummary
{
    public string Label { get; set; } = string.Empty;
    public int AssetCount { get; set; }
    public Guid? CoverAssetId { get; set; }
}

public interface ISearchCatalogService
{
    Task<List<PersonSummary>> GetPeopleAsync();
    Task<List<LabelSummary>> GetObjectLabelsAsync();
    Task<List<LabelSummary>> GetSceneLabelsAsync();
}

/// <summary>
/// Catálogos para los selectores de búsqueda y del editor de reglas smart
/// (personas, etiquetas de objetos y escenas). Cacheados por sesión de página:
/// cambian poco y los selectores los piden repetidamente.
/// </summary>
public class SearchCatalogService : ISearchCatalogService
{
    private readonly HttpClient _httpClient;
    private List<PersonSummary>? _people;
    private List<LabelSummary>? _objects;
    private List<LabelSummary>? _scenes;

    public SearchCatalogService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<List<PersonSummary>> GetPeopleAsync()
    {
        _people ??= (await _httpClient.GetFromJsonAsync<List<PersonSummary>>("/api/people")
                     ?? new List<PersonSummary>())
            .Where(p => !p.IsHidden)
            .ToList();
        return _people;
    }

    public async Task<List<LabelSummary>> GetObjectLabelsAsync()
    {
        _objects ??= await _httpClient.GetFromJsonAsync<List<LabelSummary>>("/api/objects/labels")
                     ?? new List<LabelSummary>();
        return _objects;
    }

    public async Task<List<LabelSummary>> GetSceneLabelsAsync()
    {
        _scenes ??= await _httpClient.GetFromJsonAsync<List<LabelSummary>>("/api/scenes/labels")
                    ?? new List<LabelSummary>();
        return _scenes;
    }
}
