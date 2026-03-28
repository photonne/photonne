using PhotoHub.Client.Web.Models;

namespace PhotoHub.Client.Web.Services;

/// <summary>
/// Singleton que persiste el árbol de carpetas y la lista plana entre navegaciones SPA.
/// Se invalida automáticamente tras cualquier mutación (create/update/delete/move).
/// </summary>
public class FoldersCache
{
    public List<FolderItem>? Tree { get; private set; }
    public List<FolderItem>? Folders { get; private set; }
    public bool IsValid => Tree != null && Folders != null;

    public void Set(List<FolderItem> tree, List<FolderItem> folders)
    {
        Tree = tree;
        Folders = folders;
    }

    public void Invalidate()
    {
        Tree = null;
        Folders = null;
    }
}
