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

    // Dismissed by an admin ("don't retry this asset again"). Excluded from the
    // worker, the nightly sweeps and every backfill; keeps ErrorMessage and
    // AttemptCount as an audit trail. Only a manual retry revives it.
    Suppressed = 4,
}

/// <summary>
/// What kind of failure this was, so "retry" stops being a coin flip.
///
/// <see cref="EnrichmentStatus.Failed"/> plus a null <c>NextRetryAt</c> only
/// says the attempts ran out, and they run out just as surely when the ML
/// container is down as when the JPEG is corrupt — the two need opposite
/// responses and looked identical in the failures registry.
/// </summary>
public enum EnrichmentFailureKind
{
    /// <summary>Not classified: a failure from before this existed, or one
    /// nothing recognised.</summary>
    Unknown = 0,

    /// <summary>Trying again later, with nobody touching anything, could work:
    /// the service was unreachable, timed out, or the inference raised.</summary>
    Transient = 1,

    /// <summary>The same input will fail the same way — an unreadable file, a
    /// request the service rejected. Retrying is waste; fix or suppress.</summary>
    Permanent = 2,

    /// <summary>Not the asset's fault and not self-healing: the capability is
    /// switched off, or its model never loaded. Retrying the queue does nothing
    /// until somebody fixes the service, and then everything here retries
    /// successfully at once.</summary>
    NeedsAction = 3,
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

    /// <summary>How <see cref="ErrorMessage"/> should be read. Set on every
    /// failed attempt; stays <see cref="EnrichmentFailureKind.Unknown"/> on rows
    /// that failed before this was recorded.</summary>
    public EnrichmentFailureKind FailureKind { get; set; } = EnrichmentFailureKind.Unknown;

    /// <summary>The service's own error token when there was one
    /// (<c>image_unreadable</c>, <c>model_not_loaded</c>…). Groups a wall of
    /// failures by cause without string-matching the message.</summary>
    [MaxLength(64)]
    public string? FailureCode { get; set; }

    public string? ResultJson { get; set; }

    // Backoff bookkeeping. Each Failed attempt bumps AttemptCount and pushes
    // NextRetryAt forward. The worker only picks up a task once `now >=
    // NextRetryAt`. After MaxAttempts (5) the task stays Failed for good
    // unless the user explicitly retries via the API.
    public int AttemptCount { get; set; }

    public DateTime? NextRetryAt { get; set; }
}
