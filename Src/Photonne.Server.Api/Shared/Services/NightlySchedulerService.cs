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

        await RunTasksAsync(scope.ServiceProvider, settings, ct);

        Console.WriteLine($"[NIGHTLY] Nightly run for {todayStr} completed.");
    }

    private async Task RunTasksAsync(IServiceProvider serviceProvider, SettingsService settings, CancellationToken ct)
    {
        var thumbEnabled = await settings.GetSettingAsync("NightlyTaskSettings.Thumbnails.Enabled", Guid.Empty, "false");
        var thumbMode    = await settings.GetSettingAsync("NightlyTaskSettings.Thumbnails.Mode",    Guid.Empty, "missing");
        var metaEnabled  = await settings.GetSettingAsync("NightlyTaskSettings.Metadata.Enabled",  Guid.Empty, "false");
        var metaMode     = await settings.GetSettingAsync("NightlyTaskSettings.Metadata.Mode",     Guid.Empty, "missing");

        if (thumbEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunThumbnailsAsync(serviceProvider, thumbMode == "all", ct);

        if (metaEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            await RunMetadataAsync(serviceProvider, metaMode == "all", ct);
    }

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
            .OrderBy(a => a.CreatedDate)
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
    }

    private async Task RunMetadataAsync(IServiceProvider rootProvider, bool overwriteAll, CancellationToken ct)
    {
        Console.WriteLine($"[NIGHTLY] Metadata extraction started (mode: {(overwriteAll ? "all" : "missing")}).");
        int extracted = 0, skipped = 0, failed = 0;

        using var scope = _scopeFactory.CreateScope();
        var dbContext       = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var exifService     = scope.ServiceProvider.GetRequiredService<ExifExtractorService>();
        var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();

        var assets = await dbContext.Assets
            .Include(a => a.Exif)
            .Where(a => a.Type == AssetType.IMAGE || a.Type == AssetType.VIDEO)
            .OrderBy(a => a.CreatedDate)
            .Select(a => new { a.Id, a.FullPath, a.FileName, a.Type, HasExif = a.Exif != null && a.Exif.DateTimeOriginal != null })
            .ToListAsync(ct);

        foreach (var asset in assets)
        {
            if (ct.IsCancellationRequested) break;
            try
            {
                if (!overwriteAll && asset.HasExif)
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

                var result = await exifService.ExtractExifAsync(physicalPath, ct);
                if (result != null)
                {
                    result.AssetId = asset.Id;
                    var existing = await dbContext.AssetExifs
                        .FirstOrDefaultAsync(e => e.AssetId == asset.Id, ct);
                    if (existing != null)
                        dbContext.AssetExifs.Remove(existing);
                    dbContext.AssetExifs.Add(result);
                    await dbContext.SaveChangesAsync(ct);
                    extracted++;
                }
                else
                {
                    failed++;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[NIGHTLY] Metadata error for asset {asset.Id}: {ex.Message}");
                failed++;
            }
        }

        Console.WriteLine($"[NIGHTLY] Metadata done — extracted:{extracted} skipped:{skipped} failed:{failed}.");
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
