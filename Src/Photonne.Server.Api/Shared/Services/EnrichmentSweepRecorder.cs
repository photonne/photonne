using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Per-asset ids a sweep must leave alone, split by why. Poisoned rows
/// (Failed with the backoff exhausted) are retried only by explicit admin
/// action; Suppressed rows were dismissed by an admin and are never swept.
/// </summary>
public sealed record SweepExclusions(HashSet<Guid> Poisoned, HashSet<Guid> Suppressed)
{
    public bool Contains(Guid assetId) => Poisoned.Contains(assetId) || Suppressed.Contains(assetId);
}

/// <summary>
/// Bridges the whole-library sweeps (nightly metadata/thumbnails and their
/// on-demand admin streams) onto <see cref="AssetEnrichmentTask"/> so per-asset
/// failures stop being anonymous counters: each failure is persisted with its
/// cause and counts toward the same <see cref="EnrichmentBackoff"/> budget the
/// <see cref="EnrichmentWorker"/> uses. Rows the worker currently owns
/// (Pending/Processing) and rows an admin dismissed (Suppressed) are never
/// touched here.
/// </summary>
public static class EnrichmentSweepRecorder
{
    private const int MaxErrorLength = 2000;

    /// <summary>
    /// Records one sweep failure for (asset, type): bumps AttemptCount, stores
    /// the cause and schedules/exhausts the backoff. Creates the row when the
    /// asset never went through the enrichment pipeline.
    /// </summary>
    public static async Task RecordFailureAsync(
        ApplicationDbContext db,
        Guid assetId,
        AssetEnrichmentType type,
        string error,
        CancellationToken ct)
    {
        var now = DateTime.UtcNow;
        var task = await LatestAsync(db, assetId, type, ct);

        if (task == null)
        {
            task = new AssetEnrichmentTask { AssetId = assetId, TaskType = type, CreatedAt = now };
            db.AssetEnrichmentTasks.Add(task);
        }
        else if (task.Status is EnrichmentStatus.Suppressed
                 or EnrichmentStatus.Pending
                 or EnrichmentStatus.Processing)
        {
            return;
        }

        task.AttemptCount++;
        task.Status = EnrichmentStatus.Failed;
        task.ErrorMessage = error.Length <= MaxErrorLength ? error : error[..MaxErrorLength];
        task.CompletedAt = now;
        task.NextRetryAt = EnrichmentBackoff.ComputeNextRetry(task.AttemptCount, now);
        await db.SaveChangesAsync(ct);
    }

    /// <summary>
    /// Heals a previously Failed row after the sweep processed the asset
    /// successfully (e.g. a restored file). No row, or a row in any other
    /// state, is left untouched — sweeps never mint Completed rows for the
    /// whole library.
    /// </summary>
    public static async Task RecordSuccessAsync(
        ApplicationDbContext db,
        Guid assetId,
        AssetEnrichmentType type,
        CancellationToken ct)
    {
        var task = await LatestAsync(db, assetId, type, ct);
        if (task == null || task.Status != EnrichmentStatus.Failed) return;

        task.Status = EnrichmentStatus.Completed;
        task.CompletedAt = DateTime.UtcNow;
        task.ErrorMessage = null;
        task.NextRetryAt = null;
        await db.SaveChangesAsync(ct);
    }

    /// <summary>
    /// Asset ids a sweep for <paramref name="type"/> must skip. Poisoned =
    /// Failed with retries exhausted (NextRetryAt null). Callers running an
    /// explicit overwrite/regenerate-all treat that as a manual retry and only
    /// honor the Suppressed set.
    /// </summary>
    public static async Task<SweepExclusions> GetExclusionsAsync(
        ApplicationDbContext db,
        AssetEnrichmentType type,
        CancellationToken ct)
    {
        var rows = await db.AssetEnrichmentTasks
            .AsNoTracking()
            .Where(t => t.TaskType == type &&
                (t.Status == EnrichmentStatus.Suppressed ||
                 (t.Status == EnrichmentStatus.Failed && t.NextRetryAt == null)))
            .Select(t => new { t.AssetId, t.Status })
            .ToListAsync(ct);

        var poisoned = new HashSet<Guid>();
        var suppressed = new HashSet<Guid>();
        foreach (var row in rows)
        {
            if (row.Status == EnrichmentStatus.Suppressed) suppressed.Add(row.AssetId);
            else poisoned.Add(row.AssetId);
        }
        // An asset can carry both an old poisoned row and a newer suppressed
        // one; Suppressed wins so it never shows up as "permanently failed".
        poisoned.ExceptWith(suppressed);
        return new SweepExclusions(poisoned, suppressed);
    }

    private static Task<AssetEnrichmentTask?> LatestAsync(
        ApplicationDbContext db, Guid assetId, AssetEnrichmentType type, CancellationToken ct)
        => db.AssetEnrichmentTasks
            .Where(t => t.AssetId == assetId && t.TaskType == type)
            .OrderByDescending(t => t.CreatedAt)
            .FirstOrDefaultAsync(ct);
}
