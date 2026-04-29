using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class AssetClassifiedScene
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    // Human-readable scene name from Places365 (e.g. "beach", "forest path").
    [Required]
    [MaxLength(100)]
    public string Label { get; set; } = string.Empty;

    // Numeric class index (0..364 for Places365). Useful for fast joins/filters
    // even if the label string changes between model versions.
    public int ClassId { get; set; }

    // Softmax probability in [0,1]. Higher = more confident.
    public float Confidence { get; set; }

    // 1-based rank within the predictions persisted for this asset (1 = top).
    // Lets the UI show "best guess" without re-sorting and lets the search
    // facet expose only top-1 results when desired.
    public int Rank { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
