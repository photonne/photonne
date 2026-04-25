using Pgvector;

namespace Photonne.Server.Api.Shared.Models;

public class Face
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    // Bounding box normalized to [0,1] relative to the source image.
    public float BoundingBoxX { get; set; }
    public float BoundingBoxY { get; set; }
    public float BoundingBoxW { get; set; }
    public float BoundingBoxH { get; set; }

    public float Confidence { get; set; }

    // 512-d ArcFace embedding stored as pgvector(512).
    public Vector Embedding { get; set; } = null!;

    public Guid? PersonId { get; set; }
    public Person? Person { get; set; }

    // True when the user explicitly assigned the face: clustering must never overwrite it.
    public bool IsManuallyAssigned { get; set; }

    // Soft-deleted false positive. Excluded from clustering and counted as rejected.
    public bool IsRejected { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
