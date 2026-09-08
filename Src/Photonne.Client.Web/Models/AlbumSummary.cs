namespace Photonne.Client.Web.Models;

/// <summary>Proyección de AlbumResponse que usa el workspace.</summary>
public class AlbumSummary
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTime CreatedAt { get; set; }
    public int AssetCount { get; set; }
    public string? CoverThumbnailUrl { get; set; }
    public List<string> PreviewThumbnailUrls { get; set; } = new();
    public bool IsOwner { get; set; }
    public bool IsShared { get; set; }
    public int SharedWithCount { get; set; }
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    public bool HasActiveShareLink { get; set; }
    public string Kind { get; set; } = "Manual";

    public bool IsSmart => string.Equals(Kind, "Smart", StringComparison.OrdinalIgnoreCase);

    // A un álbum smart no se le añade a mano: su contenido lo decide la regla.
    public bool AcceptsAssets => CanWrite && !IsSmart;
}
