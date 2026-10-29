using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Regenerates every user's memories once per local day, as soon as the day
/// changes in the metadata timezone (the frame the generators read "today"
/// in). "Un día como hoy" and "Este mes" depend on the date, and leaving them
/// to the nightly run tied them to its master switch (off by default), its
/// own timezone and hour, and whatever ML work runs before it — the feed
/// showed yesterday until late morning, or forever.
///
/// Honours NightlyTaskSettings.Memories.Enabled; the nightly run still
/// regenerates them after trip detection so new trips show up the same night.
/// </summary>
public sealed class MemoryDailyRefreshService(IServiceScopeFactory scopeFactory) : BackgroundService
{
    /// <summary>ISO date "yyyy-MM-dd" of the last local day refreshed (written by this service).</summary>
    public const string LastRunDateKey = "MemoryTaskSettings.LastRunDate";

    private static readonly TimeSpan StartupDelay = TimeSpan.FromMinutes(1);
    private static readonly TimeSpan PollInterval = TimeSpan.FromMinutes(1);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await Task.Delay(StartupDelay, stoppingToken);
            while (!stoppingToken.IsCancellationRequested)
            {
                try
                {
                    await TryRunAsync(stoppingToken);
                }
                catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                {
                    throw;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[MEMORIES-DAILY] Unexpected error: {ex.Message}");
                }
                await Task.Delay(PollInterval, stoppingToken);
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Graceful shutdown; the next start catches up on the day.
        }
    }

    /// <summary>Runs the refresh if this local day hasn't had one; returns whether it ran.</summary>
    internal async Task<bool> TryRunAsync(CancellationToken ct)
    {
        string today;
        using (var scope = scopeFactory.CreateScope())
        {
            var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();

            var enabled = await settings.GetSettingAsync("NightlyTaskSettings.Memories.Enabled", Guid.Empty, "true");
            if (!enabled.Equals("true", StringComparison.OrdinalIgnoreCase))
                return false;

            var tz = await MetadataTimeZone.ResolveAsync(settings, ct);
            today = MetadataTimeZone.LocalNow(tz).ToString("yyyy-MM-dd");
            var lastRunDate = await settings.GetSettingAsync(LastRunDateKey, Guid.Empty, "");
            if (lastRunDate == today)
                return false;

            // Before running, like the nightly run: a failing user must not make
            // the whole refresh repeat every minute.
            await settings.SetSettingAsync(LastRunDateKey, today, Guid.Empty);
        }

        Console.WriteLine($"[MEMORIES-DAILY] Refreshing memories for {today}.");
        var totals = await MemoryGenerationRunner.RunForAllUsersAsync(scopeFactory, "MEMORIES-DAILY", ct);
        Console.WriteLine($"[MEMORIES-DAILY] Done — users:{totals.UsersProcessed} created:{totals.Created} updated:{totals.Updated} removed:{totals.Removed} failed:{totals.UsersFailed}.");
        return true;
    }
}
