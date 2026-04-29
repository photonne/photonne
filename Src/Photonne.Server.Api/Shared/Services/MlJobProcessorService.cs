using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.FaceRecognition;
using Photonne.Server.Api.Shared.Services.ObjectDetection;
using Photonne.Server.Api.Shared.Services.SceneClassification;
using Photonne.Server.Api.Shared.Services.TextRecognition;

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
        var faceRecognition = scope.ServiceProvider.GetRequiredService<FaceRecognition.FaceRecognitionService>();
        var objectDetection = scope.ServiceProvider.GetRequiredService<ObjectDetection.ObjectDetectionService>();
        var sceneClassification = scope.ServiceProvider.GetRequiredService<SceneClassification.SceneClassificationService>();
        var textRecognition = scope.ServiceProvider.GetRequiredService<TextRecognition.TextRecognitionService>();

        var pendingJobs = await mlJobService.GetPendingJobsAsync(cancellationToken);

        if (!pendingJobs.Any())
            return;

        // The legacy "FaceDetectionWorkers" setting key is preserved on disk so
        // existing user values still take effect after the FaceDetection→FaceRecognition
        // pipeline rename. The TaskSettings UI label was already "Detección de caras".
        var faceWorkersSetting = await settingsService.GetSettingAsync("TaskSettings.FaceDetectionWorkers", Guid.Empty, "1");
        var faceWorkers = Math.Clamp(int.TryParse(faceWorkersSetting, out var n) ? n : 1, 1, 32);

        _logger.LogInformation("Processing {Count} pending ML jobs (max {Workers} concurrent)", pendingJobs.Count, faceWorkers);

        foreach (var job in pendingJobs.Take(faceWorkers))
        {
            try
            {
                await ProcessJobAsync(job, dbContext, settingsService, faceRecognition, objectDetection, sceneClassification, textRecognition, cancellationToken);
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
        FaceRecognition.FaceRecognitionService faceRecognition,
        ObjectDetection.ObjectDetectionService objectDetection,
        SceneClassification.SceneClassificationService sceneClassification,
        TextRecognition.TextRecognitionService textRecognition,
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
            case MlJobType.FaceRecognition:
                resultJson = await ProcessFaceRecognitionAsync(asset, faceRecognition, cancellationToken);
                break;
            case MlJobType.ObjectDetection:
                resultJson = await ProcessObjectDetectionAsync(asset, objectDetection, cancellationToken);
                break;
            case MlJobType.SceneClassification:
                resultJson = await ProcessSceneClassificationAsync(asset, sceneClassification, cancellationToken);
                break;
            case MlJobType.TextRecognition:
                resultJson = await ProcessTextRecognitionAsync(asset, textRecognition, cancellationToken);
                break;
        }

        job.Status = MlJobStatus.Completed;
        job.ResultJson = resultJson;
        job.CompletedAt = DateTime.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("ML job completed: JobId={JobId}, AssetId={AssetId}, JobType={JobType}",
            job.Id, job.AssetId, job.JobType);
    }

    private async Task<string> ProcessFaceRecognitionAsync(
        Asset asset,
        FaceRecognition.FaceRecognitionService faceRecognition,
        CancellationToken cancellationToken)
    {
        var count = await faceRecognition.DetectAndStoreAsync(asset.Id, cancellationToken);
        return JsonSerializer.Serialize(new { faceCount = count, model = "buffalo_l" });
    }

    private async Task<string> ProcessObjectDetectionAsync(
        Asset asset,
        ObjectDetection.ObjectDetectionService objectDetection,
        CancellationToken cancellationToken)
    {
        var count = await objectDetection.DetectAndStoreAsync(asset.Id, cancellationToken);
        return JsonSerializer.Serialize(new { objectCount = count, model = "yolov8n" });
    }
    
    private async Task<string> ProcessSceneClassificationAsync(
        Asset asset,
        SceneClassification.SceneClassificationService sceneClassification,
        CancellationToken cancellationToken)
    {
        var count = await sceneClassification.ClassifyAndStoreAsync(asset.Id, cancellationToken);
        return JsonSerializer.Serialize(new { sceneCount = count, model = "places365_resnet18" });
    }
    
    private async Task<string> ProcessTextRecognitionAsync(
        Asset asset,
        TextRecognition.TextRecognitionService textRecognition,
        CancellationToken cancellationToken)
    {
        var count = await textRecognition.RecognizeAndStoreAsync(asset.Id, cancellationToken);
        return JsonSerializer.Serialize(new { lineCount = count, model = "rapidocr" });
    }
}
