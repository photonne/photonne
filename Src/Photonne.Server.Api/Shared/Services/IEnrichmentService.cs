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
    /// Same contract as <see cref="EnqueueAsync"/> for a whole set of assets,
    /// but in a handful of round trips instead of three per asset. A backfill
    /// over a real library is tens of thousands of assets; done one at a time
    /// it takes long enough that the caller has to slice it into batches and
    /// loop, which is where the "encolando…" screens used to get stuck.
    /// Returns how many rows were actually created (assets that already had a
    /// Pending/Processing row are re-pushed into the channel, not duplicated,
    /// and don't count).
    /// </summary>
    Task<int> EnqueueManyAsync(
        IReadOnlyCollection<Guid> assetIds,
        AssetEnrichmentType taskType,
        CancellationToken cancellationToken = default);

    /// <summary>
    /// Resets an existing task row back to <see cref="EnrichmentStatus.Pending"/>
    /// (clearing AttemptCount/NextRetryAt/ErrorMessage so the backoff window
    /// starts fresh) and pushes it into the channel. Used by the retry
    /// endpoints. Returns <c>true</c> if the row existed and was reset.
    /// </summary>
    Task<bool> ResetAndEnqueueAsync(Guid taskId, CancellationToken cancellationToken = default);
}
