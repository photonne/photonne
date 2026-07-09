using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>Manual = a curated list of AlbumAsset rows (the classic album).
/// Smart = membership is derived from <see cref="Album.SmartRule"/>
/// (docs/smart-albums/).</summary>
public enum AlbumKind
{
    Manual = 0,
    Smart = 1
}

/// <summary>How a smart album's membership is produced: resolved live on read,
/// or materialized into AlbumAsset rows by a background job.</summary>
public enum SmartResolveMode
{
    Dynamic = 0,
    Materialized = 1
}

public class Album
{
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(200)]
    public string Name { get; set; } = string.Empty;

    [MaxLength(1000)]
    public string? Description { get; set; }

    // Manual (curated AlbumAsset list) vs Smart (rule-driven). See docs/smart-albums/.
    public AlbumKind Kind { get; set; } = AlbumKind.Manual;

    // Serialized SmartRuleNode tree (JSON); null for manual albums.
    public string? SmartRule { get; set; }

    public SmartResolveMode ResolveMode { get; set; } = SmartResolveMode.Dynamic;

    // Last time a Materialized smart album was reconciled into AlbumAsset rows.
    public DateTime? LastMaterializedAt { get; set; }

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
