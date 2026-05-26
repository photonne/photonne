using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Enqueues per-asset enrichment tasks (EXIF, thumbnails, ML, etc.) into the
/// background worker. Producers — the upload/sync endpoints, the nightly
/// scheduler, the admin backfill — all funnel through this single API instead
/// of running enrichment inline.
/// </summary>
public interface IEnrichmentService
{
    /// <summary>
    /// Creates (or reuses) a Pending/Processing row for the given asset+type
    /// and pushes it into the in-memory channel so the worker wakes up
    /// immediately. Safe to call repeatedly: existing rows are reused.
    /// </summary>
    Task EnqueueAsync(Guid assetId, AssetEnrichmentType taskType, CancellationToken cancellationToken = default);

    /// <summary>
    /// Resets an existing task row back to <see cref="EnrichmentStatus.Pending"/>
    /// (clearing AttemptCount/NextRetryAt/ErrorMessage so the backoff window
    /// starts fresh) and pushes it into the channel. Used by the retry
    /// endpoints. Returns <c>true</c> if the row existed and was reset.
    /// </summary>
    Task<bool> ResetAndEnqueueAsync(Guid taskId, CancellationToken cancellationToken = default);
}
