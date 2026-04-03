namespace Photonne.Server.Api.Shared.Models;

public enum AssetTagType
{
    LivePhoto,
    Burst,
    Panorama,
    Screenshot,
    HDR,
    Portrait
}

public class AssetTag
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;
    
    public AssetTagType TagType { get; set; }
    
    public double? Confidence { get; set; } // For ML-based detection
    
    public DateTime DetectedAt { get; set; } = DateTime.UtcNow;
}
