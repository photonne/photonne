using System.Globalization;
using Microsoft.AspNetCore.WebUtilities;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Estado de la página de búsqueda serializado en la query string de la URL,
/// para que atrás/recargar/compartir reproduzcan la búsqueda. ToQueryString
/// produce una forma canónica (orden fijo, vacíos omitidos) que sirve además
/// para comparar si la búsqueda de la URL ya se ejecutó.
/// </summary>
public sealed record SearchQueryState
{
    private const string DateFormat = "yyyy-MM-dd";

    public string? Query { get; init; }
    public bool Semantic { get; init; }
    public DateTime? From { get; init; }
    public DateTime? To { get; init; }
    public IReadOnlyList<Guid> PersonIds { get; init; } = Array.Empty<Guid>();
    public IReadOnlyList<string> ObjectLabels { get; init; } = Array.Empty<string>();
    public IReadOnlyList<string> SceneLabels { get; init; } = Array.Empty<string>();
    public string? Ocr { get; init; }

    public bool HasAnyFilter =>
        !string.IsNullOrWhiteSpace(Query) || From != null || To != null ||
        PersonIds.Count > 0 || ObjectLabels.Count > 0 || SceneLabels.Count > 0 ||
        !string.IsNullOrWhiteSpace(Ocr);

    /// <summary>Acepta la query cruda con o sin «?»; valores inválidos se ignoran.</summary>
    public static SearchQueryState Parse(string? queryString)
    {
        if (string.IsNullOrEmpty(queryString) || queryString == "?") return new SearchQueryState();
        var values = QueryHelpers.ParseQuery(queryString.TrimStart('?'));

        string? Get(string key) =>
            values.TryGetValue(key, out var v) && !string.IsNullOrWhiteSpace(v) ? v.ToString() : null;

        DateTime? GetDate(string key) =>
            DateTime.TryParseExact(Get(key), DateFormat, CultureInfo.InvariantCulture,
                DateTimeStyles.None, out var d) ? d : null;

        List<string> GetList(string key) =>
            (Get(key) ?? string.Empty)
                .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                .ToList();

        return new SearchQueryState
        {
            Query = Get("q"),
            Semantic = Get("sem") == "1",
            From = GetDate("from"),
            To = GetDate("to"),
            PersonIds = GetList("people")
                .Select(s => Guid.TryParse(s, out var g) ? g : Guid.Empty)
                .Where(g => g != Guid.Empty)
                .ToList(),
            ObjectLabels = GetList("objects"),
            SceneLabels = GetList("scenes"),
            Ocr = Get("ocr")
        };
    }

    /// <summary>Forma canónica sin «?» inicial; cadena vacía si no hay filtros.</summary>
    public string ToQueryString()
    {
        var parts = new List<string>();

        void Add(string key, string? value)
        {
            if (!string.IsNullOrWhiteSpace(value))
                parts.Add($"{key}={Uri.EscapeDataString(value)}");
        }

        Add("q", Query);
        if (Semantic) parts.Add("sem=1");
        Add("from", From?.ToString(DateFormat, CultureInfo.InvariantCulture));
        Add("to", To?.ToString(DateFormat, CultureInfo.InvariantCulture));
        if (PersonIds.Count > 0)
            parts.Add("people=" + string.Join(',', PersonIds));
        if (ObjectLabels.Count > 0)
            parts.Add("objects=" + string.Join(',', ObjectLabels.Select(Uri.EscapeDataString)));
        if (SceneLabels.Count > 0)
            parts.Add("scenes=" + string.Join(',', SceneLabels.Select(Uri.EscapeDataString)));
        Add("ocr", Ocr);

        return string.Join('&', parts);
    }
}
