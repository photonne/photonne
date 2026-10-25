namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// A photo someone took out of a manual album, kept for a while so the album
/// can list what was removed (who and when) and put it back where it was.
/// The AlbumAsset row itself is deleted, so every query over an album's
/// contents stays as it is. Re-adding the photo, by hand or by restoring it,
/// drops the record.
/// </summary>
public class AlbumAssetRemoval
{
    // Composite PK: (AlbumId, AssetId) — removing it again refreshes the record.
    public Guid AlbumId { get; set; }
    public Album Album { get; set; } = null!;

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    // Where it was in the album, so a restore puts it back in place.
    public int Order { get; set; }
    public DateTime AddedAt { get; set; }

    // It was the cover: a restore makes it the cover again if none was set since.
    public bool WasCover { get; set; }

    public DateTime RemovedAt { get; set; } = DateTime.UtcNow;

    // Null once that user is gone.
    public Guid? RemovedByUserId { get; set; }
    public User? RemovedByUser { get; set; }
}
