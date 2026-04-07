using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Singleton que persiste los datos del timeline entre navegaciones SPA.
/// El índice tiene un TTL de 5 minutos; las secciones se invalidan explícitamente tras mutaciones.
/// </summary>
public class TimelineCache
{
    private static readonly TimeSpan IndexTtl = TimeSpan.FromMinutes(5);

    private List<TimelineIndexItem>? _index;
    private DateTime _indexFetchedAt;
    private readonly Dictionary<string, List<TimelineItem>> _sections = new();

    public bool HasValidIndex =>
        _index != null && DateTime.UtcNow - _indexFetchedAt < IndexTtl;

    public List<TimelineIndexItem>? Index => _index;

    public void SetIndex(List<TimelineIndexItem> index)
    {
        _index = index;
        _indexFetchedAt = DateTime.UtcNow;
    }

    public bool TryGetSection(string yearMonth, out List<TimelineItem> items)
    {
        if (_sections.TryGetValue(yearMonth, out var entry))
        {
            items = entry;
            return true;
        }
        items = null!;
        return false;
    }

    public void SetSection(string yearMonth, List<TimelineItem> items)
    {
        _sections[yearMonth] = items;
    }

    public void InvalidateSection(string yearMonth)
    {
        _sections.Remove(yearMonth);
    }

    public void InvalidateAll()
    {
        _index = null;
        _sections.Clear();
    }

    /// <summary>
    /// Compara el índice actual con uno recién obtenido de la API.
    /// Devuelve true si alguna fecha tiene un conteo diferente o ha desaparecido/aparecido.
    /// </summary>
    public bool IndexChangedFrom(List<TimelineIndexItem> freshIndex)
    {
        if (_index == null) return true;
        if (_index.Count != freshIndex.Count) return true;

        var current = _index.ToDictionary(i => i.Date, i => i.Count);
        foreach (var item in freshIndex)
        {
            if (!current.TryGetValue(item.Date, out var count) || count != item.Count)
                return true;
        }
        return false;
    }

    /// <summary>
    /// Devuelve los YearMonth ("yyyy-MM") cuyo conteo ha cambiado entre el índice actual y el nuevo.
    /// </summary>
    public HashSet<string> GetChangedMonths(List<TimelineIndexItem> freshIndex)
    {
        var changed = new HashSet<string>();

        var previousByMonth = (_index ?? new())
            .GroupBy(i => $"{i.Date.Year:D4}-{i.Date.Month:D2}")
            .ToDictionary(g => g.Key, g => g.Sum(i => i.Count));

        var freshByMonth = freshIndex
            .GroupBy(i => $"{i.Date.Year:D4}-{i.Date.Month:D2}")
            .ToDictionary(g => g.Key, g => g.Sum(i => i.Count));

        foreach (var (ym, count) in freshByMonth)
        {
            if (!previousByMonth.TryGetValue(ym, out var prev) || prev != count)
                changed.Add(ym);
        }

        foreach (var ym in previousByMonth.Keys)
        {
            if (!freshByMonth.ContainsKey(ym))
                changed.Add(ym);
        }

        return changed;
    }
}
