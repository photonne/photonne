using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Background service that fires the configured nightly tasks once per day
/// at the administrator-defined local time.
///
/// Settings read from the global settings table (NightlyTaskSettings.*):
///   Enabled           — master on/off switch (default: false)
///   ScheduleTime      — "HH:mm" local time in the configured timezone (default: "02:00")
///   Timezone          — IANA timezone id (default: "UTC")
///   Thumbnails.Enabled — run thumbnail generation (default: false)
///   Thumbnails.Mode    — "missing" | "all" (default: "missing")
///   Metadata.Enabled  — run metadata extraction (default: false)
///   Metadata.Mode     — "missing" | "all" (default: "missing")
///   LastRunDate       — ISO date "yyyy-MM-dd" of last successful run (written by this service)
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
        var thumbEnabled = await settings.GetSettingAsync("NightlyTaskSettings.Thumbnails.Enabled", Guid.Empty, "false");
        var thumbMode    = await settings.GetSettingAsync("NightlyTaskSettings.Thumbnails.Mode",    Guid.Empty, "missing");
        var metaEnabled  = await settings.GetSettingAsync("NightlyTaskSettings.Metadata.Enabled",  Guid.Empty, "false");
        var metaMode     = await settings.GetSettingAsync("NightlyTaskSettings.Metadata.Mode",     Guid.Empty, "missing");
        // Face clustering — defaults to true so newly indexed faces consolidate
        // into Persons overnight without admin intervention. Cheap when nothing
        // changed (orphan count is the gate inside the clustering service).
        var faceClusterEnabled = await settings.GetSettingAsync("NightlyTaskSettings.FaceClustering.Enabled", Guid.Empty, "true");

        if (thumbEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunThumbnailsAsync(serviceProvider, thumbMode == "all", ct);

        if (metaEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMetadataAsync(serviceProvider, metaMode == "all", ct);

        if (faceClusterEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunFaceClusteringAsync(ct);
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

        await thumbnailService.RefreshThumbnailsPathAsync();

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
            .Select(a => new { a.Id, a.FullPath, a.FileName, a.Type, HasExif = a.Exif != null && a.Exif.DateTimeOriginal != null })
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
                    if (!overwriteAll && asset.HasExif)
                    {
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
