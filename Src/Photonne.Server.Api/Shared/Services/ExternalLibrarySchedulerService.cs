using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Background service that periodically checks external libraries whose CronSchedule is due
/// and triggers a scan automatically.
/// </summary>
public class ExternalLibrarySchedulerService : BackgroundService
{
    private static readonly TimeSpan PollInterval = TimeSpan.FromMinutes(1);
    private readonly IServiceScopeFactory _scopeFactory;

    public ExternalLibrarySchedulerService(IServiceScopeFactory scopeFactory)
    {
        _scopeFactory = scopeFactory;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        Console.WriteLine("[LIBRARY-SCHEDULER] Started.");

        // Recover from a prior crash/restart: any library left in Running state
        // will never be picked up again (the scheduler filters those out), so
        // flip it to Failed on startup.
        try
        {
            await RecoverStuckRunningLibrariesAsync(stoppingToken);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[LIBRARY-SCHEDULER] Recovery failed: {ex.Message}");
        }

        while (!stoppingToken.IsCancellationRequested)
        {
            await Task.Delay(PollInterval, stoppingToken);

            try
            {
                await CheckAndScanDueLibrariesAsync(stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[LIBRARY-SCHEDULER] Error: {ex.Message}");
            }
        }

        Console.WriteLine("[LIBRARY-SCHEDULER] Stopped.");
    }

    private async Task RecoverStuckRunningLibrariesAsync(CancellationToken ct)
    {
        using var scope = _scopeFactory.CreateScope();
        var dbContext     = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var notifications = scope.ServiceProvider.GetRequiredService<INotificationService>();

        var stuck = await dbContext.ExternalLibraries
            .Where(l => l.LastScanStatus == ExternalLibraryScanStatus.Running)
            .ToListAsync(ct);

        if (stuck.Count == 0) return;

        foreach (var library in stuck)
        {
            library.LastScanStatus = ExternalLibraryScanStatus.Failed;
            Console.WriteLine($"[LIBRARY-SCHEDULER] Recovered stuck Running library '{library.Name}' ({library.Id}) → Failed.");

            try
            {
                await notifications.CreateAsync(
                    library.OwnerId,
                    NotificationType.JobFailed,
                    "Escaneo de biblioteca interrumpido",
                    $"El escaneo de la biblioteca '{library.Name}' quedó bloqueado tras un reinicio del servidor y se ha marcado como fallido.");
            }
            catch { /* best effort */ }
        }

        await dbContext.SaveChangesAsync(ct);
    }

    private async Task CheckAndScanDueLibrariesAsync(CancellationToken ct)
    {
        using var scope = _scopeFactory.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        var libraries = await dbContext.ExternalLibraries
            .Where(l => l.CronSchedule != null
                     && l.LastScanStatus != ExternalLibraryScanStatus.Running)
            .ToListAsync(ct);

        var now = DateTime.UtcNow;

        foreach (var library in libraries)
        {
            if (!IsDue(library, now))
                continue;

            Console.WriteLine($"[LIBRARY-SCHEDULER] Triggering scheduled scan for library '{library.Name}' ({library.Id}).");

            // Run in a new scope so each scan gets its own DbContext
            var libraryName  = library.Name;
            var libraryId    = library.Id;
            var libraryOwner = library.OwnerId;
            _ = Task.Run(async () =>
            {
                using var scanScope = _scopeFactory.CreateScope();
                var scanService   = scanScope.ServiceProvider.GetRequiredService<ExternalLibraryScanService>();
                var notifications = scanScope.ServiceProvider.GetRequiredService<INotificationService>();

                string? lastMessage = null;
                string? errorMessage = null;
                var completed = false;

                try
                {
                    await foreach (var update in scanService.ScanAsync(libraryId, ct))
                    {
                        if (update.IsCompleted || update.Error != null)
                        {
                            lastMessage  = update.Message;
                            errorMessage = update.Error;
                            completed    = update.IsCompleted;
                            Console.WriteLine($"[LIBRARY-SCHEDULER] Library '{libraryName}': {update.Message}");
                        }
                    }
                }
                catch (Exception ex)
                {
                    errorMessage = ex.Message;
                    Console.WriteLine($"[LIBRARY-SCHEDULER] Library '{libraryName}' scan crashed: {ex.Message}");
                }

                try
                {
                    if (errorMessage != null)
                    {
                        await notifications.CreateAsync(
                            libraryOwner,
                            NotificationType.JobFailed,
                            $"Escaneo programado fallido: {libraryName}",
                            $"El escaneo automático de la biblioteca '{libraryName}' falló: {Truncate(errorMessage, 200)}");
                    }
                    else if (completed)
                    {
                        await notifications.CreateAsync(
                            libraryOwner,
                            NotificationType.JobCompleted,
                            $"Escaneo programado completado: {libraryName}",
                            string.IsNullOrEmpty(lastMessage)
                                ? "El escaneo automático de la biblioteca ha terminado correctamente."
                                : Truncate(lastMessage, 240));
                    }
                }
                catch { /* best effort */ }
            }, ct);
        }
    }

    private static string Truncate(string s, int max)
        => string.IsNullOrEmpty(s) ? string.Empty : (s.Length <= max ? s : s[..max] + "…");

    /// <summary>
    /// Returns true if the library's scheduled scan is due based on its CronSchedule and LastScannedAt.
    /// Supports common cron aliases and a simplified interval-based check for standard expressions.
    /// </summary>
    private static bool IsDue(ExternalLibrary library, DateTime now)
    {
        var interval = ParseCronInterval(library.CronSchedule!);
        if (interval == null)
            return false;

        // Never scanned yet → always due
        if (library.LastScannedAt == null)
            return true;

        return (now - library.LastScannedAt.Value) >= interval.Value;
    }

    /// <summary>
    /// Converts common cron expressions to a TimeSpan interval.
    /// Supported patterns:
    ///   @hourly  / 0 * * * *    → 1 hour
    ///   @daily   / 0 0 * * *    → 24 hours
    ///   @weekly  / 0 0 * * 0    → 7 days
    ///   @monthly / 0 0 1 * *    → 30 days
    /// Returns null for unrecognized expressions.
    /// </summary>
    internal static TimeSpan? ParseCronInterval(string cron)
    {
        var expr = cron.Trim().ToLowerInvariant();

        return expr switch
        {
            "@hourly"  or "0 * * * *"   => TimeSpan.FromHours(1),
            "@daily"   or "0 0 * * *"   => TimeSpan.FromDays(1),
            "@weekly"  or "0 0 * * 0"   => TimeSpan.FromDays(7),
            "@monthly" or "0 0 1 * *"   => TimeSpan.FromDays(30),
            _ => null
        };
    }
}
