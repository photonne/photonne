using PhotoHub.Client.Web.Models;

namespace PhotoHub.Client.Web.Services;

/// <summary>
/// Singleton que persiste la lista de álbumes entre navegaciones SPA.
/// Se invalida automáticamente tras cualquier mutación (create/update/delete/leave).
/// </summary>
public class AlbumsCache
{
    public List<AlbumItem>? Albums { get; private set; }
    public bool IsValid => Albums != null;

    public void Set(List<AlbumItem> albums) => Albums = albums;
    public void Invalidate() => Albums = null;
}
