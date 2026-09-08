using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Búsquedas puras sobre el árbol de carpetas (la respuesta anidada de
/// /api/folders/tree): cadena de ancestros para el breadcrumb y para
/// auto-expandir la ruta al nodo seleccionado.
/// </summary>
public static class FolderTreeHelper
{
    /// <summary>Cadena raíz→carpeta (ambas incluidas), o vacía si el id no está en el árbol.</summary>
    public static List<FolderItem> PathTo(IReadOnlyList<FolderItem> tree, Guid id)
    {
        var path = new List<FolderItem>();
        return FindPath(tree, id, path) ? path : new List<FolderItem>();
    }

    /// <summary>Ids de los ancestros estrictos (sin la propia carpeta).</summary>
    public static HashSet<Guid> AncestorIds(IReadOnlyList<FolderItem> tree, Guid id)
    {
        var path = PathTo(tree, id);
        return path.Count == 0
            ? new HashSet<Guid>()
            : path.Take(path.Count - 1).Select(f => f.Id).ToHashSet();
    }

    private static bool FindPath(IReadOnlyList<FolderItem> nodes, Guid id, List<FolderItem> path)
    {
        foreach (var node in nodes)
        {
            path.Add(node);
            if (node.Id == id || FindPath(node.SubFolders, id, path)) return true;
            path.RemoveAt(path.Count - 1);
        }
        return false;
    }
}
