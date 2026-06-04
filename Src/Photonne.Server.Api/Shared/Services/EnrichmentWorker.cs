using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Embeddings;
using Photonne.Server.Api.Shared.Services.FaceRecognition;
using Photonne.Server.Api.Shared.Services.ObjectDetection;
using Photonne.Server.Api.Shared.Services.SceneClassification;
using Photonne.Server.Api.Shared.Services.TextRecognition;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Long-running background service that drains the per-type channels in
/// <see cref="EnrichmentQueue"/> and runs the actual enrichment work.
///
/// Architecture:
/// <list type="bullet">
/// <item>One independent pool of workers per <see cref="AssetEnrichmentType"/>.
///   Pool size is read from <c>TaskSettings.{Type}Workers</c> at startup;
///   changing it requires restarting the server.</item>
/// <item>Producers (<see cref="EnrichmentService.EnqueueAsync"/>) push the
///   row id to the channel; workers wake up immediately — no polling.</item>
/// <item>On startup we (a) reset rows stuck in <c>Processing</c>, (b) re-push
///   every <c>Pending</c> row, and (c) re-push every <c>Failed</c> row whose
///   <c>NextRetryAt</c> has come due (with <c>AttemptCount</c> still under
///   the cap), so tasks created or expired while the service was down
///   resume cleanly.</item>
/// <item>Periodic recovery every 5 minutes catches stale <c>Processing</c>
///   rows (worker crashed mid-task), <c>Pending</c> rows whose ids were lost
///   from memory, and <c>Failed</c> rows ready to retry.</item>
/// <item>Each worker claims a task with a single atomic UPDATE (<c>Pending →
///   Processing</c> via <c>ExecuteUpdateAsync</c>). If two workers race or
///   recovery duplicates an id, only the first claim succeeds and the rest
///   skip.</item>
/// <item>Failures bump <c>AttemptCount</c> and set <c>NextRetryAt</c> using
///   <see cref="BackoffDelays"/>. After the cap the row stays <c>Failed</c>
///   permanently until the user calls the reindex endpoint.</item>
/// </list>
/// </summary>
public class EnrichmentWorker : BackgroundService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly EnrichmentQueue _queue;
    private readonly ILogger<EnrichmentWorker> _logger;

    private static readonly TimeSpan StaleProcessingTimeout = TimeSpan.FromMinutes(15);
    private static readonly TimeSpan RecoveryInterval = TimeSpan.FromMinutes(5);

    // Backoff schedule lives in EnrichmentBackoff so it can be unit-tested
    // without spinning up the worker host.

    public EnrichmentWorker(
        IServiceProvider serviceProvider,
        EnrichmentQueue queue,
        ILogger<EnrichmentWorker> logger)
    {
        _serviceProvider = serviceProvider;
        _queue = queue;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("Enrichment Worker starting");

        await MigrateLegacyWorkerSettingAsync(stoppingToken);

        var workerCounts = await ReadWorkerCountsAsync(stoppingToken);

        await RecoverStaleProcessingTasksAsync(stoppingToken);
        await RequeueDueFailedTasksAsync(stoppingToken);
        await BootstrapPendingIntoChannelsAsync(stoppingToken);

        _logger.LogInformation(
            "Enrichment workers — exif={Exif}, thumbnails={Thumbnails}, mediaRecog={MediaRecog}, " +
            "face={Face}, object={Object}, scene={Scene}, text={Text}, embedding={Embedding}",
            workerCounts.Exif, workerCounts.Thumbnails, workerCounts.MediaRecognition,
            workerCounts.FaceRecognition, workerCounts.ObjectDetection,
            workerCounts.SceneClassification, workerCounts.TextRecognition,
            workerCounts.ImageEmbedding);

        var workerTasks = new[]
        {
            RunWorkersForTypeAsync(AssetEnrichmentType.Exif,                workerCounts.Exif,                stoppingToken),
            RunWorkersForTypeAsync(AssetEnrichmentType.Thumbnails,          workerCounts.Thumbnails,          stoppingToken),
            RunWorkersForTypeAsync(AssetEnrichmentType.MediaRecognition,    workerCounts.MediaRecognition,    stoppingToken),
            RunWorkersForTypeAsync(AssetEnrichmentType.FaceRecognition,     workerCounts.FaceRecognition,     stoppingToken),
            RunWorkersForTypeAsync(AssetEnrichmentType.ObjectDetection,     workerCounts.ObjectDetection,     stoppingToken),
            RunWorkersForTypeAsync(AssetEnrichmentType.SceneClassification, workerCounts.SceneClassification, stoppingToken),
            RunWorkersForTypeAsync(AssetEnrichmentType.TextRecognition,     workerCounts.TextRecognition,     stoppingToken),
            RunWorkersForTypeAsync(AssetEnrichmentType.ImageEmbedding,      workerCounts.ImageEmbedding,      stoppingToken),
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

        _logger.LogInformation("Enrichment Worker stopped");
    }

    // ─── Worker pool ─────────────────────────────────────────────────────────

    private Task RunWorkersForTypeAsync(AssetEnrichmentType type, int workerCount, CancellationToken ct)
    {
        if (workerCount <= 0) workerCount = 1;
        var reader = _queue.Reader(type);

        var workers = Enumerable.Range(0, workerCount)
            .Select(index => Task.Run(() => RunSingleWorkerAsync(type, index, reader, ct), ct))
            .ToArray();

        return Task.WhenAll(workers);
    }

    private async Task RunSingleWorkerAsync(
        AssetEnrichmentType type,
        int workerIndex,
        System.Threading.Channels.ChannelReader<Guid> reader,
        CancellationToken ct)
    {
        await foreach (var taskId in reader.ReadAllAsync(ct))
        {
            try
            {
                await ProcessOneTaskAsync(taskId, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex,
                    "Unhandled error in {Type} worker #{Index} processing task {TaskId}",
                    type, workerIndex, taskId);
            }
        }
    }

    private async Task ProcessOneTaskAsync(Guid taskId, CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var sp = scope.ServiceProvider;
        var dbContext = sp.GetRequiredService<ApplicationDbContext>();

        // Atomic claim: only one worker wins, the rest see 0 rows affected and skip.
        // Covers the race when the queue contains duplicates (bootstrap + periodic
        // recovery, or producer + recovery) for the same task id.
        var claimed = await dbContext.AssetEnrichmentTasks
            .Where(t => t.Id == taskId && t.Status == EnrichmentStatus.Pending)
            .ExecuteUpdateAsync(s => s
                .SetProperty(t => t.Status, EnrichmentStatus.Processing)
                .SetProperty(t => t.StartedAt, DateTime.UtcNow), ct);

        if (claimed == 0) return;

        var task = await dbContext.AssetEnrichmentTasks.FirstOrDefaultAsync(t => t.Id == taskId, ct);
        if (task == null) return;

        try
        {
            var resultJson = await DispatchAsync(task, sp, ct);

            task.Status = EnrichmentStatus.Completed;
            task.ResultJson = resultJson;
            task.CompletedAt = DateTime.UtcNow;
            task.NextRetryAt = null;
            task.ErrorMessage = null;
            await dbContext.SaveChangesAsync(ct);

            _logger.LogInformation(
                "Enrichment task completed: TaskId={TaskId}, AssetId={AssetId}, TaskType={TaskType}",
                task.Id, task.AssetId, task.TaskType);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // Server is shutting down — leave the row in Processing so the next
            // startup recovery picks it up.
            throw;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error processing task {TaskId} ({TaskType})", task.Id, task.TaskType);

            task.AttemptCount++;
            task.Status = EnrichmentStatus.Failed;
            task.ErrorMessage = ex.Message.Length > 2000 ? ex.Message[..2000] : ex.Message;
            task.CompletedAt = DateTime.UtcNow;

            // Schedule the next retry if we still have backoff slots left. Otherwise
            // the row stays Failed permanently and only the manual reindex endpoint
            // (or the user via the UI) can reset it.
            task.NextRetryAt = EnrichmentBackoff.ComputeNextRetry(task.AttemptCount, DateTime.UtcNow);

            await dbContext.SaveChangesAsync(ct);

            // Only notify the asset owner once we've given up (last attempt). Earlier
            // failures are transient and silently retried — no need to flood the inbox.
            if (task.NextRetryAt == null)
            {
                await NotifyOwnerOfFailureAsync(sp, task, ex, ct);
            }
        }
    }

    private async Task NotifyOwnerOfFailureAsync(IServiceProvider sp, AssetEnrichmentTask task, Exception ex, CancellationToken ct)
    {
        try
        {
            var dbContext = sp.GetRequiredService<ApplicationDbContext>();
            var ownerId = await dbContext.Assets
                .Where(a => a.Id == task.AssetId)
                .Select(a => a.OwnerId)
                .FirstOrDefaultAsync(ct);
            if (ownerId is null || ownerId == Guid.Empty) return;

            var notifications = sp.GetRequiredService<INotificationService>();
            var label = TaskTypeLabel(task.TaskType);
            var reason = ex.Message.Length > 200 ? ex.Message[..200] + "…" : ex.Message;
            await notifications.CreateAsync(
                ownerId.Value,
                NotificationType.JobFailed,
                $"Enriquecimiento fallido: {label}",
                $"No se pudo procesar un asset ({label}) tras {task.AttemptCount} intentos. Causa: {reason}");
        }
        catch (Exception notifyEx)
        {
            _logger.LogWarning(notifyEx, "Failed to send JobFailed notification for task {TaskId}", task.Id);
        }
    }

    private static string TaskTypeLabel(AssetEnrichmentType type) => type switch
    {
        AssetEnrichmentType.Exif                => "extracción de metadatos",
        AssetEnrichmentType.Thumbnails          => "generación de miniaturas",
        AssetEnrichmentType.MediaRecognition    => "detección de tipo de medio",
        AssetEnrichmentType.FaceRecognition     => "reconocimiento facial",
        AssetEnrichmentType.ObjectDetection     => "detección de objetos",
        AssetEnrichmentType.SceneClassification => "clasificación de escenas",
        AssetEnrichmentType.TextRecognition     => "reconocimiento de texto",
        AssetEnrichmentType.ImageEmbedding      => "embeddings de imagen",
        _                                       => type.ToString()
    };

    private async Task<string?> DispatchAsync(AssetEnrichmentTask task, IServiceProvider sp, CancellationToken ct)
    {
        var dbContext = sp.GetRequiredService<ApplicationDbContext>();
        var settingsService = sp.GetRequiredService<SettingsService>();

        var asset = await dbContext.Assets
            .Include(a => a.Exif)
            .FirstOrDefaultAsync(a => a.Id == task.AssetId, ct);

        if (asset == null)
            throw new Exception($"Asset {task.AssetId} not found");

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
        if (!File.Exists(physicalPath))
            throw new FileNotFoundException($"Asset file missing at: {physicalPath}");

        switch (task.TaskType)
        {
            case AssetEnrichmentType.Exif:
            {
                var svc = sp.GetRequiredService<ExifExtractorService>();
                var exif = await svc.ExtractExifAsync(physicalPath, ct);
                if (exif == null)
                    return JsonSerializer.Serialize(new { extracted = false });

                exif.AssetId = asset.Id;
                var existing = await dbContext.AssetExifs
                    .FirstOrDefaultAsync(e => e.AssetId == asset.Id, ct);
                if (existing != null) dbContext.AssetExifs.Remove(existing);
                dbContext.AssetExifs.Add(exif);

                // Set the display timestamp used by the timeline, gated by
                // provenance: real EXIF replaces FileSystem/Inferred dates but
                // never a Manual edit; the filesystem fallback (min of
                // created/modified — rsync rewrites the birthtime) only
                // applies while the row still carries its FileSystem
                // placeholder, so it can't clobber inferred/manual dates.
                if (exif.DateTimeOriginal != null)
                {
                    if (asset.CanOverwriteCapturedAt(CaptureDateSource.Exif))
                    {
                        asset.CapturedAt = exif.DateTimeOriginal.Value;
                        asset.CapturedAtSource = CaptureDateSource.Exif;
                    }
                }
                else if (asset.CanOverwriteCapturedAt(CaptureDateSource.FileSystem))
                {
                    // Re-stat the file: the DB date columns can be stale
                    // (re-index skips already-indexed assets), while the disk
                    // may hold the rsync-preserved mtime.
                    var fileInfo = new FileInfo(physicalPath);
                    asset.RefreshFileDates(fileInfo.CreationTimeUtc, fileInfo.LastWriteTimeUtc);
                    asset.CapturedAt = asset.EffectiveFileCreatedAt;
                }
                await dbContext.SaveChangesAsync(ct);
                return JsonSerializer.Serialize(new
                {
                    extracted = true,
                    hasDateTimeOriginal = exif.DateTimeOriginal != null
                });
            }
            case AssetEnrichmentType.Thumbnails:
            {
                var svc = sp.GetRequiredService<ThumbnailGeneratorService>();
                var thumbs = await svc.GenerateThumbnailsAsync(physicalPath, asset.Id, ct);
                if (thumbs.Count == 0)
                    return JsonSerializer.Serialize(new { count = 0 });

                var existing = await dbContext.AssetThumbnails
                    .Where(t => t.AssetId == asset.Id)
                    .ToListAsync(ct);
                if (existing.Count > 0) dbContext.AssetThumbnails.RemoveRange(existing);
                dbContext.AssetThumbnails.AddRange(thumbs);
                await dbContext.SaveChangesAsync(ct);
                return JsonSerializer.Serialize(new { count = thumbs.Count });
            }
            case AssetEnrichmentType.MediaRecognition:
            {
                var svc = sp.GetRequiredService<MediaRecognitionService>();
                var tags = await svc.DetectMediaTypeAsync(physicalPath, asset.Exif, ct);

                var existing = await dbContext.AssetTags
                    .Where(t => t.AssetId == asset.Id)
                    .ToListAsync(ct);
                if (existing.Count > 0) dbContext.AssetTags.RemoveRange(existing);

                if (tags.Count > 0)
                {
                    foreach (var tag in tags)
                    {
                        dbContext.AssetTags.Add(new AssetTag
                        {
                            AssetId = asset.Id,
                            TagType = tag,
                            DetectedAt = DateTime.UtcNow
                        });
                    }
                }
                await dbContext.SaveChangesAsync(ct);
                return JsonSerializer.Serialize(new { tagCount = tags.Count });
            }
            case AssetEnrichmentType.FaceRecognition:
            {
                var svc = sp.GetRequiredService<FaceRecognitionService>();
                var count = await svc.DetectAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { faceCount = count, model = "buffalo_l" });
            }
            case AssetEnrichmentType.ObjectDetection:
            {
                var svc = sp.GetRequiredService<ObjectDetectionService>();
                var count = await svc.DetectAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { objectCount = count, model = "yolov8n" });
            }
            case AssetEnrichmentType.SceneClassification:
            {
                var svc = sp.GetRequiredService<SceneClassificationService>();
                var count = await svc.ClassifyAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { sceneCount = count, model = "places365_resnet18" });
            }
            case AssetEnrichmentType.TextRecognition:
            {
                var svc = sp.GetRequiredService<TextRecognitionService>();
                var count = await svc.RecognizeAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { lineCount = count, model = "rapidocr" });
            }
            case AssetEnrichmentType.ImageEmbedding:
            {
                var svc = sp.GetRequiredService<ImageEmbeddingService>();
                var stored = await svc.EmbedAndStoreAsync(asset.Id, ct);
                return JsonSerializer.Serialize(new { stored, model = "mclip-vit-b32" });
            }
            default:
                throw new InvalidOperationException($"Unknown enrichment task type: {task.TaskType}");
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
                    await RecoverStaleProcessingTasksAsync(ct);
                    await RequeueDueFailedTasksAsync(ct);
                    await BootstrapPendingIntoChannelsAsync(ct);
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Periodic enrichment recovery failed");
                }
            }
        }
        catch (OperationCanceledException) { /* shutdown */ }
    }

    private async Task RecoverStaleProcessingTasksAsync(CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        var threshold = DateTime.UtcNow - StaleProcessingTimeout;

        var stale = await dbContext.AssetEnrichmentTasks
            .Where(t => t.Status == EnrichmentStatus.Processing && t.StartedAt != null)
            .ToListAsync(ct);

        var toReset = stale.Where(t => t.StartedAt!.Value < threshold).ToList();
        if (toReset.Count == 0) return;

        foreach (var t in toReset)
        {
            t.Status = EnrichmentStatus.Pending;
            t.StartedAt = null;
            t.ErrorMessage = $"Recovered: was stuck in Processing past {StaleProcessingTimeout}";
        }
        await dbContext.SaveChangesAsync(ct);

        _logger.LogWarning("Recovered {Count} stale enrichment task(s) back to Pending", toReset.Count);
    }

    /// <summary>
    /// Move <c>Failed</c> rows whose backoff window has elapsed back to
    /// <c>Pending</c> so the regular bootstrap re-pushes them. Permanent
    /// Failed rows (<c>NextRetryAt == null</c> after exhausting retries) are
    /// skipped — those need an explicit manual retry from the API.
    /// </summary>
    private async Task RequeueDueFailedTasksAsync(CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        var now = DateTime.UtcNow;

        var due = await dbContext.AssetEnrichmentTasks
            .Where(t => t.Status == EnrichmentStatus.Failed
                     && t.NextRetryAt != null
                     && t.NextRetryAt <= now)
            .ToListAsync(ct);

        if (due.Count == 0) return;

        foreach (var t in due)
        {
            t.Status = EnrichmentStatus.Pending;
            t.StartedAt = null;
            // Keep AttemptCount and NextRetryAt for diagnostics; bootstrap will pick it up.
        }
        await dbContext.SaveChangesAsync(ct);

        _logger.LogInformation("Requeued {Count} Failed enrichment task(s) past their backoff window", due.Count);
    }

    private async Task BootstrapPendingIntoChannelsAsync(CancellationToken ct)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        // Push every Pending row's id into its channel. Duplicates are harmless:
        // ProcessOneTaskAsync's atomic claim ensures only one worker actually runs
        // each task. This is what makes the system self-healing across restarts.
        var pending = await dbContext.AssetEnrichmentTasks
            .AsNoTracking()
            .Where(t => t.Status == EnrichmentStatus.Pending)
            .Select(t => new { t.Id, t.TaskType })
            .ToListAsync(ct);

        foreach (var p in pending)
            await _queue.EnqueueAsync(p.TaskType, p.Id, ct);

        if (pending.Count > 0)
            _logger.LogInformation("Bootstrapped {Count} pending enrichment task(s) into channels", pending.Count);
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
            // Cheap tasks default to 2 workers (light CPU, short tasks).
            await ReadAsync(settings, "TaskSettings.ExifWorkers",                "2"),
            await ReadAsync(settings, "TaskSettings.ThumbnailsWorkers",          "2"),
            await ReadAsync(settings, "TaskSettings.MediaRecognitionWorkers",    "2"),
            // ML tasks default to 1 — they're GPU/CPU heavy and benefit from sequential processing.
            await ReadAsync(settings, "TaskSettings.FaceRecognitionWorkers",     "1"),
            await ReadAsync(settings, "TaskSettings.ObjectDetectionWorkers",     "1"),
            await ReadAsync(settings, "TaskSettings.SceneClassificationWorkers", "1"),
            await ReadAsync(settings, "TaskSettings.TextRecognitionWorkers",     "1"),
            await ReadAsync(settings, "TaskSettings.ImageEmbeddingWorkers",      "1"));

        static async Task<int> ReadAsync(SettingsService settings, string key, string defaultValue)
        {
            var raw = await settings.GetSettingAsync(key, Guid.Empty, defaultValue);
            return Math.Clamp(int.TryParse(raw, out var n) ? n : 1, 1, 32);
        }
    }

    private readonly record struct WorkerCounts(
        int Exif,
        int Thumbnails,
        int MediaRecognition,
        int FaceRecognition,
        int ObjectDetection,
        int SceneClassification,
        int TextRecognition,
        int ImageEmbedding);
}
