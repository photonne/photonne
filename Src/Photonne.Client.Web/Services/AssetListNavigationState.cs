using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Guarda la lista de assets (timeline o álbum) cuando se navega al detalle desde Timeline o Albums,
/// para que AssetDetail no tenga que pedirla de nuevo a la API.
/// </summary>
public interface IAssetListNavigationState
{
    /// <summary>Guarda la lista antes de navegar. <paramref name="albumId"/> null = timeline; con valor = álbum.</summary>
    void SetList(IReadOnlyList<TimelineItem> list, Guid? albumId = null);

    /// <summary>Recupera la lista si existe para el contexto actual (mismo albumId). La consume y la borra.</summary>
    /// <returns>La lista si había una guardada para este contexto; si no, null.</returns>
    List<TimelineItem>? TryGetList(Guid? albumId);
}

public class AssetListNavigationState : IAssetListNavigationState
{
    private List<TimelineItem>? _list;
    private Guid? _albumId;

    public void SetList(IReadOnlyList<TimelineItem> list, Guid? albumId = null)
    {
        _list = list?.ToList() ?? new List<TimelineItem>();
        _albumId = albumId;
    }

    public List<TimelineItem>? TryGetList(Guid? albumId)
    {
        if (_list == null)
            return null;

        // Mismo contexto: ambos timeline (null) o mismo álbum
        if (_albumId != albumId)
            return null;

        var result = _list;
        _list = null;
        _albumId = null;
        return result;
    }
}
