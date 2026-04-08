using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class Album
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    [Required]
    [MaxLength(200)]
    public string Name { get; set; } = string.Empty;
    
    [MaxLength(1000)]
    public string? Description { get; set; }
    
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
    
    // Cover image (thumbnail del primer asset o uno seleccionado)
    public Guid? CoverAssetId { get; set; }
    public Asset? CoverAsset { get; set; }
    
    // Propietario del álbum
    public Guid OwnerId { get; set; }
    public User Owner { get; set; } = null!;
    
    // Navigation properties
    public ICollection<AlbumAsset> AlbumAssets { get; set; } = new List<AlbumAsset>();
    public ICollection<AlbumPermission> Permissions { get; set; } = new List<AlbumPermission>();
}
