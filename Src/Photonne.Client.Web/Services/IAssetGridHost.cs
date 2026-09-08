using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Lo que <see cref="BatchActions"/> necesita de una rejilla de assets, sea la
/// de buckets (/fotos) o una colección plana (álbum, carpeta, papelera…).
/// </summary>
public interface IAssetGridHost
{
    /// <summary>Items cargados en orden visible.</summary>
    IReadOnlyList<TimelineItem> VisibleItems { get; }

    /// <summary>Subconjunto cargado de [ids], en orden visible.</summary>
    IReadOnlyList<TimelineItem> ItemsByIds(IReadOnlyCollection<Guid> ids);

    /// <summary>Retira de la vista assets que una acción sacó de la colección.</summary>
    void RemoveAssets(IReadOnlyCollection<Guid> ids);

    /// <summary>Muta in situ (favorito) y repinta.</summary>
    void UpdateAssets(IReadOnlyCollection<Guid> ids, Action<TimelineItem> mutate);

    /// <summary>
    /// Restaura la vista tras un Deshacer: la rejilla de buckets rehidrata
    /// [monthKeys]; una colección plana recarga y puede ignorar los argumentos.
    /// </summary>
    Task RestoreViewAsync(IReadOnlyCollection<Guid> ids, IReadOnlyCollection<string> monthKeys);
}
