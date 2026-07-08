using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Admin;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Background service that fires the configured nightly tasks once per day
/// at the administrator-defined local time.
///
/// Settings read from the global settings table (NightlyTaskSettings.*):
///   Enabled                       — master on/off switch (default: false)
///   ScheduleTime                  — "HH:mm" local time in the configured timezone (default: "02:00")
///   Timezone                      — IANA timezone id (default: "UTC")
///   Metadata.Enabled              — run metadata extraction (default: false)
///   Metadata.Mode                 — "missing" | "all" (default: "missing")
///   Thumbnails.Enabled            — run thumbnail generation (default: false)
///   Thumbnails.Mode               — "missing" | "all" (default: "missing")
///   FaceRecognition.Enabled       — enqueue face detection/embeddings backfill (default: false)
///   FaceRecognition.Mode          — "missing" | "all" (default: "missing")
///   FaceClustering.Enabled        — re-cluster orphan faces into Persons (default: true)
///   ObjectDetection.Enabled       — enqueue object detection backfill (default: false)
///   ObjectDetection.Mode          — "missing" | "all" (default: "missing")
///   SceneClassification.Enabled   — enqueue scene classification backfill (default: false)
///   SceneClassification.Mode      — "missing" | "all" (default: "missing")
///   TextRecognition.Enabled       — enqueue OCR backfill (default: false)
///   TextRecognition.Mode          — "missing" | "all" (default: "missing")
///   ImageEmbedding.Enabled        — enqueue CLIP embedding backfill (default: false)
///   ImageEmbedding.Mode           — "missing" | "all" (default: "missing")
///   TrashCleanup.Enabled          — apply trash retention/quota policy (default: false); uses TrashSettings.*
///   LastRunDate                   — ISO date "yyyy-MM-dd" of last successful run (written by this service)
/// </summary>
public class NightlySchedulerService : BackgroundService
{
    private static readonly TimeSpan PollInterval = TimeSpan.FromMinutes(1);
    private readonly IServiceScopeFactory _scopeFactory;

    public NightlySchedulerService(IServiceScopeFactory scopeFactory)
    {
        _scopeFactory = scopeFactory;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        Console.WriteLine("[NIGHTLY] Scheduler started.");

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await TryRunIfDueAsync(stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[NIGHTLY] Unexpected error: {ex.Message}");
            }

            await Task.Delay(PollInterval, stoppingToken);
        }

        Console.WriteLine("[NIGHTLY] Scheduler stopped.");
    }

    private async Task TryRunIfDueAsync(CancellationToken ct)
    {
        using var scope = _scopeFactory.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();

        var enabled = await settings.GetSettingAsync("NightlyTaskSettings.Enabled", Guid.Empty, "false");
        if (!enabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            return;

        var scheduleTime = await settings.GetSettingAsync("NightlyTaskSettings.ScheduleTime", Guid.Empty, "02:00");
        var timezoneId   = await settings.GetSettingAsync("NightlyTaskSettings.Timezone",     Guid.Empty, "UTC");
        var lastRunDate  = await settings.GetSettingAsync("NightlyTaskSettings.LastRunDate",  Guid.Empty, "");

        var tz = ResolveTimezone(timezoneId);
        var localNow = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);
        var todayStr = localNow.ToString("yyyy-MM-dd");

        // Already ran today — nothing to do
        if (lastRunDate == todayStr)
            return;

        // Check if current local time is past the scheduled time
        if (!TimeOnly.TryParse(scheduleTime, out var scheduled))
            scheduled = new TimeOnly(2, 0);

        var localTimeOnly = TimeOnly.FromDateTime(localNow);
        if (localTimeOnly < scheduled)
            return;

        Console.WriteLine($"[NIGHTLY] Starting nightly run for {todayStr} (local time: {localNow:HH:mm}, tz: {timezoneId}).");

        // Persist lastRunDate *before* running to avoid double-execution on error
        await settings.SetSettingAsync("NightlyTaskSettings.LastRunDate", todayStr, Guid.Empty);

        try
        {
            await RunTasksAsync(scope.ServiceProvider, settings, ct);
            Console.WriteLine($"[NIGHTLY] Nightly run for {todayStr} completed.");
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            Console.WriteLine($"[NIGHTLY] Nightly run for {todayStr} failed: {ex.Message}");
            await NotifyAdminsAsync(
                NotificationType.JobFailed,
                "Tarea nocturna abortada",
                $"La ejecución nocturna del {todayStr} se ha interrumpido por un error: {Truncate(ex.Message, 200)}",
                ct);
            throw;
        }
    }

    private async Task RunTasksAsync(IServiceProvider serviceProvider, SettingsService settings, CancellationToken ct)
    {
        // Order matches /admin/system (Sistema):
        //   1. Extracción de metadatos
        //   2. Generar miniaturas
        //   3. Reconocimiento facial      (ML backfill)
        //      └ Reagrupación facial      (clustering of detected faces)
        //   4. Reconocimiento de objetos  (ML backfill)
        //   5. Clasificación de escenas   (ML backfill)
        //   6. Reconocimiento de texto    (ML backfill — OCR)
        //   7. Búsqueda inteligente       (ML backfill — CLIP)

        var metaEnabled  = await settings.GetSettingAsync("NightlyTaskSettings.Metadata.Enabled",  Guid.Empty, "false");
        var metaMode     = await settings.GetSettingAsync("NightlyTaskSettings.Metadata.Mode",     Guid.Empty, "missing");
        var thumbEnabled = await settings.GetSettingAsync("NightlyTaskSettings.Thumbnails.Enabled", Guid.Empty, "false");
        var thumbMode    = await settings.GetSettingAsync("NightlyTaskSettings.Thumbnails.Mode",    Guid.Empty, "missing");
        var faceRecogEnabled = await settings.GetSettingAsync("NightlyTaskSettings.FaceRecognition.Enabled", Guid.Empty, "false");
        var faceRecogMode    = await settings.GetSettingAsync("NightlyTaskSettings.FaceRecognition.Mode",    Guid.Empty, "missing");
        // Face clustering — defaults to true so newly indexed faces consolidate
        // into Persons overnight without admin intervention. Cheap when nothing
        // changed (orphan count is the gate inside the clustering service).
        var faceClusterEnabled = await settings.GetSettingAsync("NightlyTaskSettings.FaceClustering.Enabled", Guid.Empty, "true");
        var objEnabled   = await settings.GetSettingAsync("NightlyTaskSettings.ObjectDetection.Enabled",     Guid.Empty, "false");
        var objMode      = await settings.GetSettingAsync("NightlyTaskSettings.ObjectDetection.Mode",        Guid.Empty, "missing");
        var sceneEnabled = await settings.GetSettingAsync("NightlyTaskSettings.SceneClassification.Enabled", Guid.Empty, "false");
        var sceneMode    = await settings.GetSettingAsync("NightlyTaskSettings.SceneClassification.Mode",    Guid.Empty, "missing");
        var textEnabled  = await settings.GetSettingAsync("NightlyTaskSettings.TextRecognition.Enabled",     Guid.Empty, "false");
        var textMode     = await settings.GetSettingAsync("NightlyTaskSettings.TextRecognition.Mode",        Guid.Empty, "missing");
        var embEnabled   = await settings.GetSettingAsync("NightlyTaskSettings.ImageEmbedding.Enabled",      Guid.Empty, "false");
        var embMode      = await settings.GetSettingAsync("NightlyTaskSettings.ImageEmbedding.Mode",         Guid.Empty, "missing");
        var trashCleanupEnabled = await settings.GetSettingAsync("NightlyTaskSettings.TrashCleanup.Enabled", Guid.Empty, "false");

        if (metaEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMetadataAsync(serviceProvider, metaMode == "all", ct);

        if (thumbEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunThumbnailsAsync(serviceProvider, thumbMode == "all", ct);

        if (faceRecogEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMlBackfillAsync(AssetEnrichmentType.FaceRecognition, faceRecogMode == "all", "reconocimiento facial", ct);

        if (faceClusterEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunFaceClusteringAsync(ct);

        if (objEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMlBackfillAsync(AssetEnrichmentType.ObjectDetection, objMode == "all", "reconocimiento de objetos", ct);

        if (sceneEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMlBackfillAsync(AssetEnrichmentType.SceneClassification, sceneMode == "all", "clasificación de escenas", ct);

        if (textEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMlBackfillAsync(AssetEnrichmentType.TextRecognition, textMode == "all", "reconocimiento de texto", ct);

        if (embEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMlBackfillAsync(AssetEnrichmentType.ImageEmbedding, embMode == "all", "búsqueda inteligente (CLIP)", ct);

        // Trash retention/quota cleanup — permanently removes expired and
        // over-quota trash (personal + shared) per TrashSettings.*.
        if (trashCleanupEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunTrashCleanupAsync(ct);
    }

    /// <summary>Applies the trash retention/quota policy overnight, reusing the
    /// same core as the admin <c>POST /api/admin/trash/cleanup-expired</c>
    /// endpoint. No-op when retention is indefinite and no quota is set.</summary>
    private async Task RunTrashCleanupAsync(CancellationToken ct)
    {
        Console.WriteLine("[NIGHTLY] Trash cleanup started.");

        using var scope = _scopeFactory.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var settings  = scope.ServiceProvider.GetRequiredService<SettingsService>();

        try
        {
            var outcome = await Features.Admin.TrashCleanupEndpoint.RunCleanupAsync(dbContext, settings, ct);
            Console.WriteLine($"[NIGHTLY] Trash cleanup done — removed:{outcome.Deleted}.");

            await NotifyAdminsAsync(
                NotificationType.JobCompleted,
                "Tarea nocturna: limpieza de papelera",
                outcome.Message,
                ct);
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            Console.WriteLine($"[NIGHTLY] Trash cleanup error: {ex.Message}");
            await NotifyAdminsAsync(
                NotificationType.JobFailed,
                "Tarea nocturna: limpieza de papelera",
                $"Error durante la limpieza: {Truncate(ex.Message, 200)}",
                ct);
        }
    }

    /// <summary>Enqueues an ML backfill batch as part of the nightly cycle. The
    /// EnrichmentWorker drains the queue at its own configured cadence;
    /// this just makes sure new (or all) assets reach the queue.</summary>
    private async Task RunMlBackfillAsync(AssetEnrichmentType jobType, bool reprocessAll, string label, CancellationToken ct)
    {
        Console.WriteLine($"[NIGHTLY] {label} started (mode: {(reprocessAll ? "all" : "missing")}).");

        using var scope = _scopeFactory.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var mlJobs    = scope.ServiceProvider.GetRequiredService<IEnrichmentService>();

        try
        {
            // batchSize: null → enqueue everything that matches; the ML processor
            // will pace itself. Bounded by the dedup query that excludes assets
            // already Pending/Processing for the same job type.
            var result = await MlBackfillRunner.EnqueueAsync(
                dbContext, mlJobs, jobType,
                onlyMissing: !reprocessAll,
                batchSize: null,
                ownerScope: null,
                ct);

            Console.WriteLine($"[NIGHTLY] {label} done — enqueued:{result.Enqueued} of {result.Total}.");

            await NotifyAdminsAsync(
                NotificationType.JobCompleted,
                $"Tarea nocturna: {label}",
                $"Encolados {result.Enqueued} de {result.Total} asset(s) pendientes. El procesador ML los completará en segundo plano.",
                ct);
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            Console.WriteLine($"[NIGHTLY] {label} error: {ex.Message}");
            await NotifyAdminsAsync(
                NotificationType.JobFailed,
                $"Tarea nocturna: {label}",
                $"Error encolando jobs: {Truncate(ex.Message, 200)}",
                ct);
        }
    }

    /// <summary>Sends the same notification to every active admin user. Used by
    /// nightly tasks so administrators see a daily summary in the bell menu.</summary>
    private async Task NotifyAdminsAsync(NotificationType type, string title, string message, CancellationToken ct)
    {
        try
        {
            using var scope = _scopeFactory.CreateScope();
            var dbContext     = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
            var notifications = scope.ServiceProvider.GetRequiredService<INotificationService>();

            var adminIds = await dbContext.Users
                .Where(u => u.Role == "Admin" && u.IsActive)
                .Select(u => u.Id)
                .ToListAsync(ct);

            foreach (var adminId in adminIds)
                await notifications.CreateAsync(adminId, type, title, message);
        }
        catch (Exception ex)
        {
            // Never let a notification failure mask the underlying task result.
            Console.WriteLine($"[NIGHTLY] Failed to dispatch admin notification '{title}': {ex.Message}");
        }
    }

    private async Task RunFaceClusteringAsync(CancellationToken ct)
    {
        Console.WriteLine("[NIGHTLY] Face clustering started.");
        int totalCreated = 0, ownersProcessed = 0, ownersFailed = 0;

        using var scope = _scopeFactory.CreateScope();
        var dbContext     = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var clustering    = scope.ServiceProvider.GetRequiredService<FaceRecognition.FaceClusteringService>();
        var notifications = scope.ServiceProvider.GetRequiredService<INotificationService>();

        // One pass per asset-owning user. Identity moved to UserFaceAssignment;
        // we no longer have a single global "orphan" notion, so we cluster for
        // every user that owns at least one detected face. Users with shared-
        // only access cluster lazily on /people via EnsureUpToDateForUserAsync.
        var ownerIds = await dbContext.Faces
            .Select(f => f.Asset.OwnerId)
            .Where(id => id != null)
            .Distinct()
            .ToListAsync(ct);

        foreach (var oid in ownerIds.OfType<Guid>())
        {
            if (ct.IsCancellationRequested) break;
            try
            {
                var created = await clustering.RunForUserAsync(oid, ct);
                totalCreated += created;
                ownersProcessed++;

                // Only ping the owner when the pass actually consolidated new
                // people — otherwise it's a no-op they don't need to hear about.
                if (created > 0)
                {
                    await notifications.CreateAsync(
                        oid,
                        NotificationType.JobCompleted,
                        "Agrupación de rostros completada",
                        $"Se han creado {created} persona(s) nuevas a partir de los rostros detectados en tus fotos.",
                        actionUrl: "/people");
                }
            }
            catch (Exception ex)
            {
                ownersFailed++;
                Console.WriteLine($"[NIGHTLY] Face clustering error for owner {oid}: {ex.Message}");
                try
                {
                    await notifications.CreateAsync(
                        oid,
                        NotificationType.JobFailed,
                        "Error en agrupación de rostros",
                        $"La tarea nocturna de agrupación de rostros falló: {Truncate(ex.Message, 200)}");
                }
                catch { /* best effort */ }
            }
        }

        Console.WriteLine($"[NIGHTLY] Face clustering done — owners:{ownersProcessed} new persons:{totalCreated}.");

        await NotifyAdminsAsync(
            ownersFailed > 0 ? NotificationType.JobFailed : NotificationType.JobCompleted,
            "Tarea nocturna: agrupación de rostros",
            $"Procesados {ownersProcessed} usuario(s), {totalCreated} persona(s) nuevas, {ownersFailed} error(es).",
            ct);
    }

    private static string Truncate(string s, int max)
        => string.IsNullOrEmpty(s) ? string.Empty : (s.Length <= max ? s : s[..max] + "…");

    private async Task RunThumbnailsAsync(IServiceProvider rootProvider, bool regenerateAll, CancellationToken ct)
    {
        Console.WriteLine($"[NIGHTLY] Thumbnail generation started (mode: {(regenerateAll ? "all" : "missing")}).");
        int generated = 0, skipped = 0, failed = 0;

        using var scope = _scopeFactory.CreateScope();
        var dbContext        = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var thumbnailService = scope.ServiceProvider.GetRequiredService<ThumbnailGeneratorService>();
        var settingsService  = scope.ServiceProvider.GetRequiredService<SettingsService>();

        var assets = await dbContext.Assets
            .OrderBy(a => a.FileCreatedAt)
            .Select(a => new { a.Id, a.FullPath, a.FileName })
            .ToListAsync(ct);

        foreach (var asset in assets)
        {
            if (ct.IsCancellationRequested) break;
            try
            {
                var missing = thumbnailService.GetMissingThumbnailSizes(asset.Id);
                if (!regenerateAll && missing.Count == 0)
                {
                    skipped++;
                    continue;
                }

                var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (!File.Exists(physicalPath))
                {
                    failed++;
                    continue;
                }

                var result = await thumbnailService.GenerateThumbnailsAsync(physicalPath, asset.Id, ct);
                if (result.Count > 0)
                {
                    var existing = await dbContext.AssetThumbnails
                        .Where(t => t.AssetId == asset.Id)
                        .ToListAsync(ct);
                    dbContext.AssetThumbnails.RemoveRange(existing);
                    dbContext.AssetThumbnails.AddRange(result);
                    await dbContext.SaveChangesAsync(ct);
                    generated += result.Count;
                }
                else
                {
                    failed++;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[NIGHTLY] Thumbnail error for asset {asset.Id}: {ex.Message}");
                failed++;
            }
        }

        Console.WriteLine($"[NIGHTLY] Thumbnails done — generated:{generated} skipped:{skipped} failed:{failed}.");

        await NotifyAdminsAsync(
            failed > 0 ? NotificationType.JobFailed : NotificationType.JobCompleted,
            "Tarea nocturna: miniaturas",
            $"Generadas {generated}, omitidas {skipped}, fallidas {failed}.",
            ct);
    }

    private async Task RunMetadataAsync(IServiceProvider rootProvider, bool overwriteAll, CancellationToken ct)
    {
        Console.WriteLine($"[NIGHTLY] Metadata extraction started (mode: {(overwriteAll ? "all" : "missing")}).");
        int extracted = 0, skipped = 0, failed = 0;

        using var scope = _scopeFactory.CreateScope();
        var dbContext       = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();

        // Worker count (TaskSettings.MetadataWorkers, default 2, clamped to [1..32])
        var workersRaw = await settingsService.GetSettingAsync(
            "TaskSettings.MetadataWorkers", Guid.Empty, "2");
        var metadataWorkers = Math.Clamp(int.TryParse(workersRaw, out var w) ? w : 2, 1, 32);

        var assets = await dbContext.Assets
            .Include(a => a.Exif)
            .Where(a => a.Type == AssetType.Image || a.Type == AssetType.Video)
            .OrderBy(a => a.FileCreatedAt)
            .Select(a => new
            {
                a.Id, a.FullPath, a.FileName, a.Type, a.CapturedAt, a.CapturedAtSource,
                ExifDate = a.Exif != null ? a.Exif.DateTimeOriginal : null
            })
            .ToListAsync(ct);

        await Parallel.ForEachAsync(
            assets,
            new ParallelOptions { MaxDegreeOfParallelism = metadataWorkers, CancellationToken = ct },
            async (asset, innerCt) =>
            {
                using var innerScope = _scopeFactory.CreateScope();
                var innerDb       = innerScope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                var innerExif     = innerScope.ServiceProvider.GetRequiredService<ExifExtractorService>();
                var innerSettings = innerScope.ServiceProvider.GetRequiredService<SettingsService>();

                try
                {
                    if (!overwriteAll && asset.ExifDate != null)
                    {
                        // Same contract as the admin extraction endpoint: even
                        // when skipping re-extraction, re-sync CapturedAt with
                        // the EXIF already stored so a divergence introduced by
                        // a later re-index gets healed by the nightly pass.
                        // Manual dates stay untouched (provenance gate).
                        if (asset.CapturedAt != asset.ExifDate.Value &&
                            asset.CapturedAtSource != CaptureDateSource.Manual)
                        {
                            var skippedRow = await innerDb.Assets
                                .FirstOrDefaultAsync(a => a.Id == asset.Id, innerCt);
                            if (skippedRow != null && skippedRow.CanOverwriteCapturedAt(CaptureDateSource.Exif))
                            {
                                skippedRow.CapturedAt = asset.ExifDate.Value;
                                skippedRow.CapturedAtSource = CaptureDateSource.Exif;
                                await innerDb.SaveChangesAsync(innerCt);
                            }
                        }
                        Interlocked.Increment(ref skipped);
                        return;
                    }

                    var physicalPath = await innerSettings.ResolvePhysicalPathAsync(asset.FullPath);
                    if (!File.Exists(physicalPath))
                    {
                        Interlocked.Increment(ref failed);
                        return;
                    }

                    var result = await innerExif.ExtractExifAsync(physicalPath, innerCt);
                    if (result != null)
                    {
                        result.AssetId = asset.Id;
                        var existing = await innerDb.AssetExifs
                            .FirstOrDefaultAsync(e => e.AssetId == asset.Id, innerCt);
                        if (existing != null)
                            innerDb.AssetExifs.Remove(existing);
                        innerDb.AssetExifs.Add(result);

                        // Mirror the EnrichmentWorker contract: keep CapturedAt
                        // in sync with the freshly-extracted EXIF so the nightly
                        // job can correct timeline order for assets where the
                        // original extraction missed DateTimeOriginal. Gated by
                        // provenance — EXIF never clobbers Manual; the filesystem
                        // fallback never clobbers Inferred/Manual.
                        var assetRow = await innerDb.Assets
                            .FirstOrDefaultAsync(a => a.Id == asset.Id, innerCt);
                        if (assetRow != null)
                        {
                            if (result.DateTimeOriginal != null)
                            {
                                if (assetRow.CanOverwriteCapturedAt(CaptureDateSource.Exif))
                                {
                                    assetRow.CapturedAt = result.DateTimeOriginal.Value;
                                    assetRow.CapturedAtSource = CaptureDateSource.Exif;
                                }
                            }
                            else if (assetRow.CanOverwriteCapturedAt(CaptureDateSource.FileSystem))
                            {
                                // Re-stat the file: the DB date columns can be
                                // stale (re-index skips already-indexed assets),
                                // while the disk may hold the rsync-preserved mtime.
                                var fileInfo = new FileInfo(physicalPath);
                                assetRow.RefreshFileDates(fileInfo.CreationTimeUtc, fileInfo.LastWriteTimeUtc);
                                assetRow.CapturedAt = assetRow.EffectiveFileCreatedAt;
                            }
                        }

                        await innerDb.SaveChangesAsync(innerCt);
                        Interlocked.Increment(ref extracted);
                    }
                    else
                    {
                        Interlocked.Increment(ref failed);
                    }
                }
                catch (OperationCanceledException) { throw; }
                catch (Exception ex)
                {
                    Console.WriteLine($"[NIGHTLY] Metadata error for asset {asset.Id}: {ex.Message}");
                    Interlocked.Increment(ref failed);
                }
            });

        Console.WriteLine($"[NIGHTLY] Metadata done — extracted:{extracted} skipped:{skipped} failed:{failed}.");

        await NotifyAdminsAsync(
            failed > 0 ? NotificationType.JobFailed : NotificationType.JobCompleted,
            "Tarea nocturna: metadatos",
            $"Extraídos {extracted}, omitidos {skipped}, fallidos {failed}.",
            ct);
    }

    private static TimeZoneInfo ResolveTimezone(string ianaId)
    {
        try { return TimeZoneInfo.FindSystemTimeZoneById(ianaId); }
        catch
        {
            if (TimeZoneInfo.TryConvertIanaIdToWindowsId(ianaId, out var winId))
            {
                try { return TimeZoneInfo.FindSystemTimeZoneById(winId); }
                catch { /* fall through */ }
            }
            return TimeZoneInfo.Utc;
        }
    }
}
