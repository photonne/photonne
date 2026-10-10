namespace Photonne.Server.Api.Shared.Models;

public enum AssetTagType
{
    LivePhoto,
    Burst,
    Panorama,
    Screenshot,
    HDR,
    Portrait,
    // The motion (.mov) half of an iOS Live Photo. Stored so the timeline can
    // hide it — the clip should never surface as a standalone video next to its
    // still; the still owns the pairing and serves the clip via /motion.
    // Appended last on purpose: TagType persists as an int, so existing rows
    // keep their values.
    MotionPhotoPart
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
