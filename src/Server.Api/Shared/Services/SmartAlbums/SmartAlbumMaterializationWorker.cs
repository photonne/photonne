namespace Photonne.Server.Api.Shared.Services.SmartAlbums;

/// <summary>
/// Periodic sweep that re-materializes every smart album
/// (<see cref="SmartAlbumMaterializer"/>). Opening an album already refreshes
/// it; the sweep keeps the ones nobody opens current — their members are what
/// the album grants its readers, and what their counts and covers show in the
/// album list — after indexing, enrichment, favorites or permission changes.
/// The first sweep, at startup, also backfills albums created before
/// membership was materialized.
/// </summary>
public sealed class SmartAlbumMaterializationWorker : BackgroundService
{
    private static readonly TimeSpan StartupDelay = TimeSpan.FromSeconds(30);
    private static readonly TimeSpan SweepInterval = TimeSpan.FromMinutes(15);

    private readonly IServiceScopeFactory _scopeFactory;
    private readonly ILogger<SmartAlbumMaterializationWorker> _logger;

    public SmartAlbumMaterializationWorker(
        IServiceScopeFactory scopeFactory,
        ILogger<SmartAlbumMaterializationWorker> logger)
    {
        _scopeFactory = scopeFactory;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await Task.Delay(StartupDelay, stoppingToken);
            while (!stoppingToken.IsCancellationRequested)
            {
                try
                {
                    using var scope = _scopeFactory.CreateScope();
                    var materializer = scope.ServiceProvider.GetRequiredService<SmartAlbumMaterializer>();
                    await materializer.MaterializeAllAsync(stoppingToken);
                }
                catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                {
                    throw;
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Smart album sweep failed; retrying in {Interval}", SweepInterval);
                }

                await Task.Delay(SweepInterval, stoppingToken);
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Graceful shutdown.
        }
    }
}
