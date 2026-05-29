namespace Photonne.Client.Web.Models;

// Modelos del visor público de enlaces compartidos (acceso anónimo por token).
// La gestión de enlaces (crear, editar, revocar, listar enviados) vive en las
// apps nativas, así que aquí solo se conserva lo que el visor necesita.

public class SharedContentResponse
{
    public string Token { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty; // "asset" | "album"
    public bool RequiresPassword { get; set; }
    public bool WrongPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public SharedAssetInfo? Asset { get; set; }
    public SharedAlbumInfo? Album { get; set; }
    public List<SharedAssetInfo>? Assets { get; set; }
    public DateTime? ExpiresAt { get; set; }
}

public class SharedAssetInfo
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty;
    public DateTime FileCreatedAt { get; set; }
    public long FileSize { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public string ThumbnailUrl { get; set; } = string.Empty;
    public string ContentUrl { get; set; } = string.Empty;

    public double AspectRatio => Width.HasValue && Height.HasValue && Height.Value > 0
        ? (double)Width.Value / Height.Value : 1.0;
}

public class SharedAlbumInfo
{
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public int AssetCount { get; set; }
    public string? CoverThumbnailUrl { get; set; }
}
