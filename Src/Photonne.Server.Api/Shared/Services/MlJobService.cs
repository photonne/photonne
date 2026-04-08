using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class MlJobService : IMlJobService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly ILogger<MlJobService> _logger;
    
    public MlJobService(ApplicationDbContext dbContext, ILogger<MlJobService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }
    
    public async Task EnqueueMlJobAsync(Guid assetId, MlJobType jobType, CancellationToken cancellationToken = default)
    {
        // Check if a job already exists (pending or processing)
        var existingJob = await _dbContext.AssetMlJobs
            .FirstOrDefaultAsync(j => 
                j.AssetId == assetId && 
                j.JobType == jobType && 
                (j.Status == MlJobStatus.Pending || j.Status == MlJobStatus.Processing),
                cancellationToken);
        
        if (existingJob != null)
            return; // Job already exists for this asset
        
        var job = new AssetMlJob
        {
            AssetId = assetId,
            JobType = jobType,
            Status = MlJobStatus.Pending,
            CreatedAt = DateTime.UtcNow
        };
        
        _dbContext.AssetMlJobs.Add(job);
        await _dbContext.SaveChangesAsync(cancellationToken);
        
        _logger.LogInformation("ML job enqueued: AssetId={AssetId}, JobType={JobType}", assetId, jobType);
    }
    
    public async Task<List<AssetMlJob>> GetPendingJobsAsync(CancellationToken cancellationToken = default)
    {
        return await _dbContext.AssetMlJobs
            .Where(j => j.Status == MlJobStatus.Pending)
            .OrderBy(j => j.CreatedAt)
            .ToListAsync(cancellationToken);
    }
}
