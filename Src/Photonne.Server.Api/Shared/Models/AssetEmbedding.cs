using System.ComponentModel.DataAnnotations;
using Pgvector;

namespace Photonne.Server.Api.Shared.Models;

// One CLIP-family embedding vector per asset. Used for free-text semantic search.
// Re-embedding on a model upgrade is signalled by a different ModelVersion — the
// processor upserts the row when the version differs.
public class AssetEmbedding
{
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    // L2-normalized image embedding. Dimensionality is fixed to match the
    // active model (M-CLIP ViT-B-32 = 512). A future model swap that changes
    // the dimensionality requires a migration.
    public Vector Embedding { get; set; } = null!;

    [Required]
    [MaxLength(64)]
    public string ModelVersion { get; set; } = string.Empty;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
