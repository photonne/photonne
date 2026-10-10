namespace Photonne.Server.Api.Shared.Models;

public class AlbumAsset
{
    // Composite PK: (AlbumId, AssetId)
    public Guid AlbumId { get; set; }
    public Album Album { get; set; } = null!;

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    // Orden personalizado dentro del álbum
    public int Order { get; set; }

    public DateTime AddedAt { get; set; } = DateTime.UtcNow;
}
