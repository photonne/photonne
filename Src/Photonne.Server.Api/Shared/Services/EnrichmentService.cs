using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class EnrichmentService : IEnrichmentService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly EnrichmentQueue _queue;
    private readonly ILogger<EnrichmentService> _logger;

    // How many asset ids travel in one round trip of the bulk path. Big enough
    // that a 100k backfill is ~50 queries, small enough to stay well under the
    // parameter ceiling Npgsql enforces on an IN list.
    private const int ChunkSize = 2_000;

    public EnrichmentService(
        ApplicationDbContext dbContext,
        EnrichmentQueue queue,
        ILogger<EnrichmentService> logger)
    {
        _dbContext = dbContext;
        _queue = queue;
        _logger = logger;
    }

    public async Task EnqueueAsync(
        Guid assetId,
        AssetEnrichmentType taskType,
        CancellationToken cancellationToken = default)
    {
        // Reuse an existing Pending/Processing row for this asset+type so we never
        // create duplicates. We still push its id into the channel — covers the case
        // where the row pre-existed (server restart) and the in-memory queue lost
        // track of it. Failed rows are NOT reused: the caller wants a fresh attempt.
        var existing = await _dbContext.AssetEnrichmentTasks
            .FirstOrDefaultAsync(t =>
                t.AssetId == assetId &&
                t.TaskType == taskType &&
                (t.Status == EnrichmentStatus.Pending || t.Status == EnrichmentStatus.Processing),
                cancellationToken);

        if (existing != null)
        {
            await _queue.EnqueueAsync(taskType, existing.Id, cancellationToken);
            return;
        }

        var task = new AssetEnrichmentTask
        {
            AssetId = assetId,
            TaskType = taskType,
            Status = EnrichmentStatus.Pending,
            CreatedAt = DateTime.UtcNow,
        };

        _dbContext.AssetEnrichmentTasks.Add(task);
        await _dbContext.SaveChangesAsync(cancellationToken);

        await _queue.EnqueueAsync(taskType, task.Id, cancellationToken);

        _logger.LogInformation(
            "Enrichment task enqueued: AssetId={AssetId}, TaskType={TaskType}",
            assetId, taskType);
    }

    /// <summary>
    /// Bulk sibling of <see cref="EnqueueAsync"/>. One query to find which of
    /// these assets already have a live row, one insert for the rest, then the
    /// channel pushes — instead of a SELECT + INSERT + SaveChanges per asset.
    /// The ids go in as chunks so a backfill over a six-figure library doesn't
    /// build a single parameter list the driver refuses.
    /// </summary>
    public async Task<int> EnqueueManyAsync(
        IReadOnlyCollection<Guid> assetIds,
        AssetEnrichmentType taskType,
        CancellationToken cancellationToken = default)
    {
        if (assetIds.Count == 0) return 0;

        var created = 0;
        foreach (var chunk in assetIds.Distinct().Chunk(ChunkSize))
        {
            cancellationToken.ThrowIfCancellationRequested();

            // Existing Pending/Processing rows are reused, exactly as the
            // single-asset path does: we still push their ids so a row the
            // in-memory queue lost track of (server restart) gets picked up.
            var existing = await _dbContext.AssetEnrichmentTasks
                .AsNoTracking()
                .Where(t => chunk.Contains(t.AssetId)
                    && t.TaskType == taskType
                    && (t.Status == EnrichmentStatus.Pending || t.Status == EnrichmentStatus.Processing))
                .Select(t => new { t.Id, t.AssetId })
                .ToListAsync(cancellationToken);

            var alreadyQueued = existing.Select(e => e.AssetId).ToHashSet();

            var fresh = chunk
                .Where(id => !alreadyQueued.Contains(id))
                .Select(id => new AssetEnrichmentTask
                {
                    AssetId = id,
                    TaskType = taskType,
                    Status = EnrichmentStatus.Pending,
                    CreatedAt = DateTime.UtcNow,
                })
                .ToList();

            if (fresh.Count > 0)
            {
                _dbContext.AssetEnrichmentTasks.AddRange(fresh);
                await _dbContext.SaveChangesAsync(cancellationToken);
                created += fresh.Count;
            }

            foreach (var e in existing)
                await _queue.EnqueueAsync(taskType, e.Id, cancellationToken);
            foreach (var t in fresh)
                await _queue.EnqueueAsync(taskType, t.Id, cancellationToken);

            // Detach what we just inserted: tracked entities pile up across
            // chunks and EF's change detection gets quadratically slower as they
            // do. Targeted rather than ChangeTracker.Clear() — the DbContext is
            // scoped to the request, so clearing it would also throw away
            // whatever the caller was tracking.
            foreach (var t in fresh)
                _dbContext.Entry(t).State = EntityState.Detached;
        }

        _logger.LogInformation(
            "Enrichment tasks enqueued in bulk: Assets={Assets}, Created={Created}, TaskType={TaskType}",
            assetIds.Count, created, taskType);

        return created;
    }

    public async Task<bool> ResetAndEnqueueAsync(Guid taskId, CancellationToken cancellationToken = default)
    {
        var task = await _dbContext.AssetEnrichmentTasks
            .FirstOrDefaultAsync(t => t.Id == taskId, cancellationToken);
        if (task == null) return false;

        task.Status = EnrichmentStatus.Pending;
        task.AttemptCount = 0;
        task.NextRetryAt = null;
        task.ErrorMessage = null;
        // The retry is a fresh verdict: keeping the old classification would
        // leave the row badged "permanente" while it queues up to try again.
        task.FailureKind = EnrichmentFailureKind.Unknown;
        task.FailureCode = null;
        task.StartedAt = null;
        task.CompletedAt = null;
        await _dbContext.SaveChangesAsync(cancellationToken);

        await _queue.EnqueueAsync(task.TaskType, task.Id, cancellationToken);

        _logger.LogInformation(
            "Enrichment task reset and re-enqueued: TaskId={TaskId}, AssetId={AssetId}, TaskType={TaskType}",
            task.Id, task.AssetId, task.TaskType);

        return true;
    }
}
