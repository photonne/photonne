using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Guarda la lista de assets cuando se navega al detalle desde cualquier origen
/// (timeline, álbum, carpeta, mapa, favoritos, búsqueda…) para que AssetDetail
/// no tenga que pedirla de nuevo a la API y la navegación prev/next sea coherente
/// con el contexto de origen.
/// </summary>
public interface IAssetListNavigationState
{
    /// <summary>
    /// Guarda la lista antes de navegar.
    /// <paramref name="contextKey"/> identifica el origen: "album:{id}", "folder:{id}", "map", "favorites", etc.
    /// Si es null se asume "timeline".
    /// </summary>
    void SetList(IReadOnlyList<TimelineItem> list, string? contextKey = null);

    /// <summary>Sobrecarga de conveniencia para álbumes (mantiene compat con llamadas existentes).</summary>
    void SetList(IReadOnlyList<TimelineItem> list, Guid? albumId);

    /// <summary>Recupera la lista si existe para el contexto dado. Devuelve null si no coincide.</summary>
    List<TimelineItem>? TryGetList(string? contextKey);

    /// <summary>Sobrecarga de conveniencia para álbumes.</summary>
    List<TimelineItem>? TryGetList(Guid? albumId);
}

public class AssetListNavigationState : IAssetListNavigationState
{
    private List<TimelineItem>? _list;
    private string? _contextKey;

    public void SetList(IReadOnlyList<TimelineItem> list, string? contextKey = null)
    {
        _list = list?.ToList() ?? new List<TimelineItem>();
        _contextKey = contextKey;
    }

    public void SetList(IReadOnlyList<TimelineItem> list, Guid? albumId)
    {
        SetList(list, albumId.HasValue ? $"album:{albumId.Value}" : null);
    }

    public List<TimelineItem>? TryGetList(string? contextKey)
    {
        if (_list == null)
            return null;

        if (!string.Equals(_contextKey, contextKey, StringComparison.Ordinal))
            return null;

        return _list;
    }

    public List<TimelineItem>? TryGetList(Guid? albumId)
    {
        return TryGetList(albumId.HasValue ? $"album:{albumId.Value}" : null);
    }
}
