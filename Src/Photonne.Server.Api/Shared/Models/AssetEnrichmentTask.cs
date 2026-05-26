using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// Every per-asset post-backup task: EXIF extraction, thumbnail generation,
/// media-type detection, and the ML jobs. Lives in a single table so the
/// worker, the UI and the admin tooling all reason about enrichment uniformly.
/// </summary>
public enum AssetEnrichmentType
{
    // Cheap, runs first in practice. Populates the AssetExif row that
    // MediaRecognition reads. If MediaRecognition runs first it just
    // produces no tags (best-effort, no failure).
    Exif = 0,

    // Generates the three thumbnail sizes. Independent of EXIF/ML.
    Thumbnails = 1,

    // Detects panorama / screenshot / live photo / burst / HDR. Depends on
    // EXIF being available to produce useful results.
    MediaRecognition = 2,

    // The five ML pipelines. Each runs its own model and may be slow
    // (seconds per asset). Independent of EXIF/Thumbnails.
    FaceRecognition = 10,
    ObjectDetection = 11,
    SceneClassification = 12,
    TextRecognition = 13,
    ImageEmbedding = 14,
}

public enum EnrichmentStatus
{
    Pending,
    Processing,
    Completed,
    Failed,
}

public class AssetEnrichmentTask
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    public AssetEnrichmentType TaskType { get; set; }

    public EnrichmentStatus Status { get; set; } = EnrichmentStatus.Pending;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime? StartedAt { get; set; }

    public DateTime? CompletedAt { get; set; }

    [MaxLength(2000)]
    public string? ErrorMessage { get; set; }

    public string? ResultJson { get; set; }

    // Backoff bookkeeping. Each Failed attempt bumps AttemptCount and pushes
    // NextRetryAt forward. The worker only picks up a task once `now >=
    // NextRetryAt`. After MaxAttempts (5) the task stays Failed for good
    // unless the user explicitly retries via the API.
    public int AttemptCount { get; set; }

    public DateTime? NextRetryAt { get; set; }
}
