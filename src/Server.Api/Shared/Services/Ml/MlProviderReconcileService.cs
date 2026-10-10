using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Shared.Services.Ml;

/// <summary>
/// On startup, pushes the admin-configured per-task compute device (the
/// <c>*.Provider</c> settings) to the ML service so its live providers match
/// what the DB says — the ML container only knows its env defaults until told
/// otherwise, so without this a container restart would silently drop the
/// admin's GPU/CPU choices. Best-effort: retries a handful of times while the
/// ML service is still starting, then gives up (the ML runs on its env defaults
/// and the admin can re-save to re-sync).
/// </summary>
public class MlProviderReconcileService : BackgroundService
{
    private readonly IServiceProvider _services;
    private readonly ILogger<MlProviderReconcileService> _logger;

    private static readonly TimeSpan RetryDelay = TimeSpan.FromSeconds(15);
    private const int MaxAttempts = 6;

    public MlProviderReconcileService(IServiceProvider services, ILogger<MlProviderReconcileService> logger)
    {
        _services = services;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        for (var attempt = 1; attempt <= MaxAttempts && !stoppingToken.IsCancellationRequested; attempt++)
        {
            try
            {
                using var scope = _services.CreateScope();
                var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
                var mlConfig = scope.ServiceProvider.GetRequiredService<IMlConfigClient>();

                var allOk = true;
                foreach (var (key, task) in MlProviders.KeyToTask)
                {
                    var device = await settings.GetSettingAsync(key, Guid.Empty, MlProviders.DeviceAuto);
                    var spec = MlProviders.DeviceToProviderSpec(device);
                    allOk &= await mlConfig.SetProviderAsync(task, spec, stoppingToken);
                }

                if (allOk)
                {
                    _logger.LogInformation("Reconciled ML task providers with admin settings");
                    return;
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "ML provider reconcile attempt {Attempt}/{Max} failed", attempt, MaxAttempts);
            }

            if (attempt < MaxAttempts)
            {
                try { await Task.Delay(RetryDelay, stoppingToken); }
                catch (OperationCanceledException) { return; }
            }
        }

        _logger.LogWarning(
            "Gave up reconciling ML task providers after {Max} attempts; the ML service is running on its env defaults",
            MaxAttempts);
    }
}
