namespace Photonne.Client.Web.Models;

/// <summary>Subconjunto de AlbumResponse que necesita el workspace (picker de álbum).</summary>
public class AlbumSummary
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public int AssetCount { get; set; }
    public string? CoverThumbnailUrl { get; set; }
    public bool CanWrite { get; set; }
    public string Kind { get; set; } = "Manual";

    // A un álbum smart no se le añade a mano: su contenido lo decide la regla.
    public bool AcceptsAssets => CanWrite &&
        !string.Equals(Kind, "Smart", StringComparison.OrdinalIgnoreCase);
}
