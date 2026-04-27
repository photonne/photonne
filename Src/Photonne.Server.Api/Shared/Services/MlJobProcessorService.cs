using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Shared.Services;

public class MlJobProcessorService : BackgroundService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly ILogger<MlJobProcessorService> _logger;
    private readonly TimeSpan _pollInterval = TimeSpan.FromSeconds(30);
    // Jobs in Processing whose StartedAt is older than this are assumed crashed
    // (the worker died, the process was restarted, etc.) and bumped back to
    // Pending so they get a second chance instead of staying wedged forever.
    private static readonly TimeSpan StaleProcessingTimeout = TimeSpan.FromMinutes(15);

    public MlJobProcessorService(
        IServiceProvider serviceProvider,
        ILogger<MlJobProcessorService> logger)
    {
        _serviceProvider = serviceProvider;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("ML Job Processor Service started");

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await RecoverStaleProcessingJobsAsync(stoppingToken);
                await ProcessPendingJobsAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing ML jobs");
            }

            await Task.Delay(_pollInterval, stoppingToken);
        }

        _logger.LogInformation("ML Job Processor Service stopped");
    }

    private async Task RecoverStaleProcessingJobsAsync(CancellationToken cancellationToken)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        var threshold = DateTime.UtcNow - StaleProcessingTimeout;
        // Materialize first so the WHERE on a UTC-converted column doesn't trip
        // on Npgsql's strict timestamp-without-timezone enforcement.
        var stale = await dbContext.AssetMlJobs
            .Where(j => j.Status == MlJobStatus.Processing && j.StartedAt != null)
            .ToListAsync(cancellationToken);

        var toReset = stale.Where(j => j.StartedAt!.Value < threshold).ToList();
        if (toReset.Count == 0) return;

        foreach (var j in toReset)
        {
            j.Status = MlJobStatus.Pending;
            j.StartedAt = null;
            j.ErrorMessage = $"Recovered: was stuck in Processing since {j.StartedAt:o}";
        }
        await dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogWarning("Recovered {Count} stale ML job(s) back to Pending (timeout={Timeout})",
            toReset.Count, StaleProcessingTimeout);
    }

    private async Task ProcessPendingJobsAsync(CancellationToken cancellationToken)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var mlJobService = scope.ServiceProvider.GetRequiredService<IMlJobService>();
        var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();
        var faceDetection = scope.ServiceProvider.GetRequiredService<FaceRecognition.FaceDetectionService>();

        var pendingJobs = await mlJobService.GetPendingJobsAsync(cancellationToken);

        if (!pendingJobs.Any())
            return;

        var faceWorkersSetting = await settingsService.GetSettingAsync("TaskSettings.FaceDetectionWorkers", Guid.Empty, "1");
        var faceWorkers = Math.Clamp(int.TryParse(faceWorkersSetting, out var n) ? n : 1, 1, 32);

        _logger.LogInformation("Processing {Count} pending ML jobs (max {Workers} concurrent)", pendingJobs.Count, faceWorkers);

        foreach (var job in pendingJobs.Take(faceWorkers))
        {
            try
            {
                await ProcessJobAsync(job, dbContext, settingsService, faceDetection, cancellationToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing job {JobId}", job.Id);
                job.Status = MlJobStatus.Failed;
                job.ErrorMessage = ex.Message.Length > 2000 ? ex.Message[..2000] : ex.Message;
                job.CompletedAt = DateTime.UtcNow;
                await dbContext.SaveChangesAsync(cancellationToken);
            }
        }
    }

    private async Task ProcessJobAsync(
        AssetMlJob job,
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        FaceRecognition.FaceDetectionService faceDetection,
        CancellationToken cancellationToken)
    {
        job.Status = MlJobStatus.Processing;
        job.StartedAt = DateTime.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);

        // Load asset
        var asset = await dbContext.Assets
            .Include(a => a.Exif)
            .FirstOrDefaultAsync(a => a.Id == job.AssetId, cancellationToken);

        if (asset == null)
        {
            throw new Exception($"Asset {job.AssetId} not found");
        }

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);

        if (!File.Exists(physicalPath))
        {
            throw new FileNotFoundException($"Asset {job.AssetId} file not found at: {physicalPath}");
        }

        // Process based on job type
        string? resultJson = null;

        switch (job.JobType)
        {
            case MlJobType.FaceDetection:
                resultJson = await ProcessFaceDetectionAsync(asset, faceDetection, cancellationToken);
                break;
            case MlJobType.ObjectRecognition:
                resultJson = await ProcessObjectRecognitionAsync(asset, cancellationToken);
                break;
            case MlJobType.SceneClassification:
                resultJson = await ProcessSceneClassificationAsync(asset, cancellationToken);
                break;
            case MlJobType.TextRecognition:
                resultJson = await ProcessTextRecognitionAsync(asset, cancellationToken);
                break;
        }

        job.Status = MlJobStatus.Completed;
        job.ResultJson = resultJson;
        job.CompletedAt = DateTime.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("ML job completed: JobId={JobId}, AssetId={AssetId}, JobType={JobType}",
            job.Id, job.AssetId, job.JobType);
    }

    private async Task<string> ProcessFaceDetectionAsync(
        Asset asset,
        FaceRecognition.FaceDetectionService faceDetection,
        CancellationToken cancellationToken)
    {
        var count = await faceDetection.DetectAndStoreAsync(asset.Id, cancellationToken);
        return JsonSerializer.Serialize(new { faceCount = count, model = "buffalo_l" });
    }
    
    private async Task<string> ProcessObjectRecognitionAsync(Asset asset, CancellationToken cancellationToken)
    {
        // TODO: Integrate with ML library
        await Task.CompletedTask;
        _logger.LogInformation("Object recognition processing for asset {AssetId} - placeholder implementation", asset.Id);
        return "{}";
    }
    
    private async Task<string> ProcessSceneClassificationAsync(Asset asset, CancellationToken cancellationToken)
    {
        // TODO: Integrate with ML library
        await Task.CompletedTask;
        _logger.LogInformation("Scene classification processing for asset {AssetId} - placeholder implementation", asset.Id);
        return "{}";
    }
    
    private async Task<string> ProcessTextRecognitionAsync(Asset asset, CancellationToken cancellationToken)
    {
        // TODO: Integrate with ML library
        await Task.CompletedTask;
        _logger.LogInformation("Text recognition processing for asset {AssetId} - placeholder implementation", asset.Id);
        return "{}";
    }
}
