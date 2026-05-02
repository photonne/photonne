using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class MlJobService : IMlJobService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly MlJobQueue _queue;
    private readonly ILogger<MlJobService> _logger;

    public MlJobService(ApplicationDbContext dbContext, MlJobQueue queue, ILogger<MlJobService> logger)
    {
        _dbContext = dbContext;
        _queue = queue;
        _logger = logger;
    }

    public async Task EnqueueMlJobAsync(Guid assetId, MlJobType jobType, CancellationToken cancellationToken = default)
    {
        // Reuse the existing Pending/Processing job if one is already queued for this
        // asset+type so we never create duplicates. We still push its id into the
        // channel — covers the case where the row pre-existed (e.g. created before a
        // server restart) and the in-memory queue lost track of it.
        var existingJob = await _dbContext.AssetMlJobs
            .FirstOrDefaultAsync(j =>
                j.AssetId == assetId &&
                j.JobType == jobType &&
                (j.Status == MlJobStatus.Pending || j.Status == MlJobStatus.Processing),
                cancellationToken);

        if (existingJob != null)
        {
            await _queue.EnqueueAsync(jobType, existingJob.Id, cancellationToken);
            return;
        }

        var job = new AssetMlJob
        {
            AssetId = assetId,
            JobType = jobType,
            Status = MlJobStatus.Pending,
            CreatedAt = DateTime.UtcNow
        };

        _dbContext.AssetMlJobs.Add(job);
        await _dbContext.SaveChangesAsync(cancellationToken);

        await _queue.EnqueueAsync(jobType, job.Id, cancellationToken);

        _logger.LogInformation("ML job enqueued: AssetId={AssetId}, JobType={JobType}", assetId, jobType);
    }
}
