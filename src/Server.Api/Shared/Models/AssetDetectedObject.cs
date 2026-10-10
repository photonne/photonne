using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class AssetDetectedObject
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    // Human-readable class name from the detection model (e.g. "dog", "car").
    [Required]
    [MaxLength(100)]
    public string Label { get; set; } = string.Empty;

    // Numeric class index reported by the model. Useful for fast joins/filters
    // even if the label string changes between model versions.
    public int ClassId { get; set; }

    public float Confidence { get; set; }

    // Bounding box normalized to [0,1] relative to the source image.
    public float BoundingBoxX { get; set; }
    public float BoundingBoxY { get; set; }
    public float BoundingBoxW { get; set; }
    public float BoundingBoxH { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
