using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class EnrichmentService : IEnrichmentService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly EnrichmentQueue _queue;
    private readonly ILogger<EnrichmentService> _logger;

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

    public async Task<bool> ResetAndEnqueueAsync(Guid taskId, CancellationToken cancellationToken = default)
    {
        var task = await _dbContext.AssetEnrichmentTasks
            .FirstOrDefaultAsync(t => t.Id == taskId, cancellationToken);
        if (task == null) return false;

        task.Status = EnrichmentStatus.Pending;
        task.AttemptCount = 0;
        task.NextRetryAt = null;
        task.ErrorMessage = null;
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
