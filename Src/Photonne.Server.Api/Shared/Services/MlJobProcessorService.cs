using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.FaceRecognition;
using Photonne.Server.Api.Shared.Services.ObjectDetection;
using Photonne.Server.Api.Shared.Services.SceneClassification;
using Photonne.Server.Api.Shared.Services.TextRecognition;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Long-running background service that drains the per-type channels in
/// <see cref="MlJobQueue"/> and runs the actual ML work.
///
/// Architecture:
/// <list type="bullet">
/// <item>One independent pool of workers per <see cref="MlJobType"/>. Pool size
///   is read from <c>TaskSettings.{Type}Workers</c> at startup; changing it
///   requires restarting the server.</item>
/// <item>Producers (mostly <see cref="MlJobService.EnqueueMlJobAsync"/>) push
///   the row id to the channel; workers wake up immediately — no 30-second
///   poll like the previous implementation.</item>
/// <item>On startup we (a) reset rows stuck in Processing, (b) re-push every
///   Pending row into its channel so jobs created while the service was down
///   resume.</item>
/// <item>Periodic recovery every 5 minutes catches stale Processing rows
///   (worker crashed mid-job) and Pending rows whose ids may have been lost
///   from memory if Enqueue was called outside this process model.</item>
/// <item>Each worker claims a job with a single atomic UPDATE (<c>Pending →
///   Processing</c> via <see cref="EntityFrameworkQueryableExtensions"/>'s
///   <c>ExecuteUpdateAsync</c>). If two workers race or recovery duplicates
///   an id, only the first claim succeeds and the rest skip.</item>
/// </list>
/// </summary>
public class MlJobProcessorService : BackgroundService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly MlJobQueue _queue;
    private readonly ILogger<MlJobProcessorService> _logger;

    // Processing rows older than this on startup or during periodic recovery
    // are assumed orphaned (worker crashed, server restarted mid-job) and
    // bumped back to Pending so they get a second chance.
    private static readonly TimeSpan StaleProcessingTimeout = TimeSpan.FromMinutes(15);
    private static readonly TimeSpan RecoveryInterval = TimeSpan.FromMinutes(5);

    public MlJobProcessorService(
        IServiceProvider serviceProvider,
        MlJobQueue queue,
        ILogger<MlJobProcessorService> logger)
    {
        _serviceProvider = serviceProvider;
        _queue = queue;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("ML Job Processor Service starting");

        await MigrateLegacyWorkerSettingAsync(stoppingToken);

        var workerCounts = await ReadWorkerCountsAsync(stoppingToken);

        await RecoverStaleProcessingJobsAsync(stoppingToken);
        await BootstrapPendingIntoChannelsAsync(stoppingToken);

        _logger.LogInformation(
            "ML workers — face={Face}, object={Object}, scene={Scene}, text={Text}",
            workerCounts.FaceRecognition, workerCounts.ObjectDetection,
            workerCounts.SceneClassification, workerCounts.TextRecognition);

        var workerTasks = new[]
        {
            RunWorkersForTypeAsync(MlJobType.FaceRecognition,     workerCounts.FaceRecognition,     stoppingToken),
            RunWorkersForTypeAsync(MlJobType.ObjectDetection,     workerCounts.ObjectDetection,     stoppingToken),
            RunWorkersForTypeAsync(MlJobType.SceneClassification, workerCounts.SceneClassification, stoppingToken),
            RunWorkersForTypeAsync(MlJobType.TextRecognition,     workerCounts.TextRecognition,     stoppingToken),
            RunPeriodicRecoveryAsync(stoppingToken),
        };

        try
        {
            await Task.WhenAll(workerTasks);
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Graceful shutdown — the channels are unbounded so we just stop reading.
        }

        _logger.LogInformation("ML Job Processor Service stopped");
    }

    // ─── Worker pool ─────────────────────────────────────────────────────────

    private Task RunWorkersForTypeAsync(MlJobType type, int workerCount, CancellationToken ct)
    {
        if (workerCount <= 0) workerCount = 1;
        var reader = _queue.Reader(type);

        var workers = Enumerable.Range(0, workerCount)
            .Select(index => Task.Run(() => RunSingleWorkerAsync(type, index, reader, ct), ct))
            .ToArray();

        return Task.WhenAll(workers);
    }

    private async Task RunSingleWorkerAsync(MlJobType type, int workerIndex, System.Threading.Channels.ChannelReader<Guid> reader, CancellationToken ct)
    {
        await foreach (var jobId in reader.ReadAllAsync(ct))
        {
            try
            {
                await ProcessOneJobAsync(jobId, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex,
                    "Unhandled error in {Type} worker #{Index} processing job {JobId}",
                    type, workerIndex, jobId);
            }
        }
    }

    private async Task ProcessOneJobAsync(Guid jobId, CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var sp = scope.ServiceProvider;
        var dbContext = sp.GetRequiredService<ApplicationDbContext>();

        // Atomic claim: only one worker wins, the rest see 0 rows affected and skip.
        // Covers the race when the queue contains duplicates (bootstrap + periodic
        // recovery, or producer + recovery) for the same job id.
        var claimed = await dbContext.AssetMlJobs
            .Where(j => j.Id == jobId && j.Status == MlJobStatus.Pending)
            .ExecuteUpdateAsync(s => s
                .SetProperty(j => j.Status, MlJobStatus.Processing)
                .SetProperty(j => j.StartedAt, DateTime.UtcNow), ct);

        if (claimed == 0) return;

        var job = await dbContext.AssetMlJobs.FirstOrDefaultAsync(j => j.Id == jobId, ct);
        if (job == null) return;

        try
        {
            var resultJson = await DispatchAsync(job, sp, ct);

            job.Status = MlJobStatus.Completed;
            job.ResultJson = resultJson;
            job.CompletedAt = DateTime.UtcNow;
            await dbContext.SaveChangesAsync(ct);

            _logger.LogInformation("ML job completed: JobId={JobId}, AssetId={AssetId}, JobType={JobType}",
                job.Id, job.AssetId, job.JobType);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // Server is shutting down — leave the row in Processing so the next
            // startup recovery picks it up.
            throw;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error processing job {JobId} ({JobType})", job.Id, job.JobType);
            job.Status = MlJobStatus.Failed;
            job.ErrorMessage = ex.Message.Length > 2000 ? ex.Message[..2000] : ex.Message;
            job.CompletedAt = DateTime.UtcNow;
            await dbContext.SaveChangesAsync(ct);
        }
    }

    private async Task<string?> DispatchAsync(AssetMlJob job, IServiceProvider sp, CancellationToken ct)
    {
        var dbContext = sp.GetRequiredService<ApplicationDbContext>();
        var settingsService = sp.GetRequiredService<SettingsService>();

        var asset = await dbContext.Assets
            .Include(a => a.Exif)
            .FirstOrDefaultAsync(a => a.Id == job.AssetId, ct);

        if (asset == null)
            throw new Exception($"Asset {job.AssetId} not found");

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
        if (!File.Exists(physicalPath))
            throw new FileNotFoundException($"Asset file missing at: {physicalPath}");

        switch (job.JobType)
        {
            case MlJobType.FaceRecognition:
            {
                var svc = sp.GetRequiredService<FaceRecognitionService>();
                var count = await svc.DetectAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { faceCount = count, model = "buffalo_l" });
            }
            case MlJobType.ObjectDetection:
            {
                var svc = sp.GetRequiredService<ObjectDetectionService>();
                var count = await svc.DetectAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { objectCount = count, model = "yolov8n" });
            }
            case MlJobType.SceneClassification:
            {
                var svc = sp.GetRequiredService<SceneClassificationService>();
                var count = await svc.ClassifyAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { sceneCount = count, model = "places365_resnet18" });
            }
            case MlJobType.TextRecognition:
            {
                var svc = sp.GetRequiredService<TextRecognitionService>();
                var count = await svc.RecognizeAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { lineCount = count, model = "rapidocr" });
            }
            default:
                throw new InvalidOperationException($"Unknown ML job type: {job.JobType}");
        }
    }

    // ─── Recovery ────────────────────────────────────────────────────────────

    private async Task RunPeriodicRecoveryAsync(CancellationToken ct)
    {
        try
        {
            while (!ct.IsCancellationRequested)
            {
                await Task.Delay(RecoveryInterval, ct);
                try
                {
                    await RecoverStaleProcessingJobsAsync(ct);
                    await BootstrapPendingIntoChannelsAsync(ct);
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Periodic ML job recovery failed");
                }
            }
        }
        catch (OperationCanceledException) { /* shutdown */ }
    }

    private async Task RecoverStaleProcessingJobsAsync(CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        var threshold = DateTime.UtcNow - StaleProcessingTimeout;

        // Materialize first so the WHERE on a UTC-converted column doesn't trip
        // on Npgsql's strict timestamp-without-timezone enforcement.
        var stale = await dbContext.AssetMlJobs
            .Where(j => j.Status == MlJobStatus.Processing && j.StartedAt != null)
            .ToListAsync(ct);

        var toReset = stale.Where(j => j.StartedAt!.Value < threshold).ToList();
        if (toReset.Count == 0) return;

        foreach (var j in toReset)
        {
            j.Status = MlJobStatus.Pending;
            j.StartedAt = null;
            j.ErrorMessage = $"Recovered: was stuck in Processing past {StaleProcessingTimeout}";
        }
        await dbContext.SaveChangesAsync(ct);

        _logger.LogWarning("Recovered {Count} stale ML job(s) back to Pending", toReset.Count);
    }

    private async Task BootstrapPendingIntoChannelsAsync(CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        // Push every Pending row's id into its channel. Duplicates are harmless:
        // ProcessOneJobAsync's atomic claim ensures only one worker actually runs
        // each job. This is what makes the system self-healing across restarts.
        var pending = await dbContext.AssetMlJobs
            .AsNoTracking()
            .Where(j => j.Status == MlJobStatus.Pending)
            .Select(j => new { j.Id, j.JobType })
            .ToListAsync(ct);

        foreach (var p in pending)
            await _queue.EnqueueAsync(p.JobType, p.Id, ct);

        if (pending.Count > 0)
            _logger.LogInformation("Bootstrapped {Count} pending ML job(s) into channels", pending.Count);
    }

    // ─── Settings ────────────────────────────────────────────────────────────

    /// <summary>One-shot at startup: copy the legacy <c>FaceDetectionWorkers</c>
    /// value to the new <c>FaceRecognitionWorkers</c> key if the new one is
    /// missing. Lets users keep their tuned value across the rename.</summary>
    private async Task MigrateLegacyWorkerSettingAsync(CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();

        var newValue = await settings.GetSettingAsync("TaskSettings.FaceRecognitionWorkers", Guid.Empty);
        if (!string.IsNullOrEmpty(newValue)) return;

        var legacyValue = await settings.GetSettingAsync("TaskSettings.FaceDetectionWorkers", Guid.Empty);
        if (string.IsNullOrEmpty(legacyValue)) return;

        await settings.SetSettingAsync("TaskSettings.FaceRecognitionWorkers", legacyValue, Guid.Empty);
        _logger.LogInformation(
            "Migrated TaskSettings.FaceDetectionWorkers={Value} → TaskSettings.FaceRecognitionWorkers",
            legacyValue);
    }

    private async Task<WorkerCounts> ReadWorkerCountsAsync(CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();

        return new WorkerCounts(
            await ReadAsync(settings, "TaskSettings.FaceRecognitionWorkers", "1"),
            await ReadAsync(settings, "TaskSettings.ObjectDetectionWorkers", "1"),
            await ReadAsync(settings, "TaskSettings.SceneClassificationWorkers", "1"),
            await ReadAsync(settings, "TaskSettings.TextRecognitionWorkers", "1"));

        static async Task<int> ReadAsync(SettingsService settings, string key, string defaultValue)
        {
            var raw = await settings.GetSettingAsync(key, Guid.Empty, defaultValue);
            return Math.Clamp(int.TryParse(raw, out var n) ? n : 1, 1, 32);
        }
    }

    private readonly record struct WorkerCounts(
        int FaceRecognition,
        int ObjectDetection,
        int SceneClassification,
        int TextRecognition);
}
