namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// A user's personal pin on a folder they can read (own, shared with them, or an
/// external library). Same shape as <see cref="AlbumPin"/>: Colecciones mixes
/// both in its "Fijados" section, and a pin never changes what anyone else sees.
/// </summary>
public class FolderPin
{
    // Composite PK: (UserId, FolderId)
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    public Guid FolderId { get; set; }
    public Folder Folder { get; set; } = null!;

    public DateTime PinnedAt { get; set; } = DateTime.UtcNow;
}
