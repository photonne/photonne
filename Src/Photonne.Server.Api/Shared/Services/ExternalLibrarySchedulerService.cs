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
    private static readonly TimeSpan PollInterval = TimeSpan.FromMinutes(5);
    private readonly IServiceScopeFactory _scopeFactory;

    public ExternalLibrarySchedulerService(IServiceScopeFactory scopeFactory)
    {
        _scopeFactory = scopeFactory;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        Console.WriteLine("[LIBRARY-SCHEDULER] Started.");

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
            _ = Task.Run(async () =>
            {
                using var scanScope = _scopeFactory.CreateScope();
                var scanService = scanScope.ServiceProvider.GetRequiredService<ExternalLibraryScanService>();

                await foreach (var update in scanService.ScanAsync(library.Id, ct))
                {
                    if (update.IsCompleted || update.Error != null)
                        Console.WriteLine($"[LIBRARY-SCHEDULER] Library '{library.Name}': {update.Message}");
                }
            }, ct);
        }
    }

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
