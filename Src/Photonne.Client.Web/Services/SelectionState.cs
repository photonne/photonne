namespace Photonne.Client.Web.Services;

/// <summary>
/// Estado de la selección múltiple del workspace, como clase pura (testeable
/// sin navegador). Mantiene el conjunto y el ANCLA del Shift+clic: la última
/// celda tocada individualmente. El rango se resuelve contra el orden visible
/// que aporta la rejilla en el momento del clic.
/// </summary>
public class SelectionState
{
    private readonly HashSet<Guid> _selected = new();
    private Guid? _anchor;

    public event Action? Changed;

    public IReadOnlyCollection<Guid> Selected => _selected;
    public int Count => _selected.Count;
    public bool IsActive => _selected.Count > 0;
    public Guid? Anchor => _anchor;

    public bool IsSelected(Guid id) => _selected.Contains(id);

    /// <summary>Alterna una celda y la convierte en ancla del próximo rango.</summary>
    public void Toggle(Guid id)
    {
        if (!_selected.Add(id)) _selected.Remove(id);
        _anchor = id;
        Changed?.Invoke();
    }

    /// <summary>
    /// Shift+clic: selecciona el tramo ancla→destino (ambos inclusive) sobre
    /// [visibleOrder]. Sin ancla válida degrada a <see cref="Toggle"/> y la
    /// celda pasa a anclar el siguiente rango. El ancla no se mueve, como en
    /// los gestores de ficheros: shift+clics sucesivos re-extienden desde ella.
    /// </summary>
    public void SelectRange(Guid targetId, IReadOnlyList<Guid> visibleOrder)
    {
        var anchorIndex = _anchor.HasValue ? IndexOf(visibleOrder, _anchor.Value) : -1;
        var targetIndex = IndexOf(visibleOrder, targetId);
        if (anchorIndex < 0 || targetIndex < 0)
        {
            Toggle(targetId);
            return;
        }

        var lo = Math.Min(anchorIndex, targetIndex);
        var hi = Math.Max(anchorIndex, targetIndex);
        for (var i = lo; i <= hi; i++)
        {
            _selected.Add(visibleOrder[i]);
        }
        Changed?.Invoke();
    }

    /// <summary>Marca o desmarca en bloque (checkbox de mes, pintado por arrastre).</summary>
    public void SetSelected(IEnumerable<Guid> ids, bool selected)
    {
        var changed = false;
        foreach (var id in ids)
        {
            changed |= selected ? _selected.Add(id) : _selected.Remove(id);
        }
        if (changed) Changed?.Invoke();
    }

    public void Clear()
    {
        if (_selected.Count == 0 && _anchor == null) return;
        _selected.Clear();
        _anchor = null;
        Changed?.Invoke();
    }

    /// <summary>Ninguno / parcial / todos, para el checkbox tri-estado de mes.</summary>
    public (bool Any, bool All) StateOf(IReadOnlyCollection<Guid> ids)
    {
        if (ids.Count == 0) return (false, false);
        var count = ids.Count(_selected.Contains);
        return (count > 0, count == ids.Count);
    }

    private static int IndexOf(IReadOnlyList<Guid> order, Guid id)
    {
        for (var i = 0; i < order.Count; i++)
        {
            if (order[i] == id) return i;
        }
        return -1;
    }
}
