using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public enum ThumbnailSize
{
    Small = 220,    // 220x220px
    Medium = 640,   // 640x640px
    Large = 1280    // 1280x1280px
}

public class AssetThumbnail
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;
    
    public ThumbnailSize Size { get; set; }
    
    [Required]
    [MaxLength(1000)]
    public string FilePath { get; set; } = string.Empty;
    
    public int Width { get; set; }
    public int Height { get; set; }
    
    public long FileSize { get; set; }

    [MaxLength(20)]
    public string Format { get; set; } = "JPEG"; // JPEG, WebP

    // Dominant color of the image, stored as hex "#rrggbb". Only populated for Small thumbnails.
    [MaxLength(7)]
    public string? DominantColor { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

