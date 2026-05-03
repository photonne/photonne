namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

/// <summary>
/// Drains <see cref="FaceClusteringQueue"/> and runs
/// <see cref="FaceClusteringService.EnsureUpToDateForUserAsync"/> per user on
/// its own DI scope. Decouples the lazy clustering pass — which can scan
/// thousands of shared-but-unassigned faces — from the <c>/api/people</c>
/// request thread that triggered it.
///
/// Single worker by design: a pass is per-user and the queue dedupes per user,
/// so concurrency would only buy us throughput across distinct users — not
/// worth the complexity given how rarely this fires (cooldown-gated, ~once
/// per user every couple of minutes at most).
/// </summary>
public class FaceClusteringBackgroundService : BackgroundService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly FaceClusteringQueue _queue;
    private readonly ILogger<FaceClusteringBackgroundService> _logger;

    public FaceClusteringBackgroundService(
        IServiceProvider serviceProvider,
        FaceClusteringQueue queue,
        ILogger<FaceClusteringBackgroundService> logger)
    {
        _serviceProvider = serviceProvider;
        _queue = queue;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("Face Clustering Background Service starting");

        try
        {
            await foreach (var userId in _queue.Reader.ReadAllAsync(stoppingToken))
            {
                try
                {
                    using var scope = _serviceProvider.CreateScope();
                    var clustering = scope.ServiceProvider.GetRequiredService<FaceClusteringService>();
                    await clustering.EnsureUpToDateForUserAsync(userId, stoppingToken);
                }
                catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                {
                    throw;
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex,
                        "Lazy clustering pass for user {UserId} failed; next /people request will retry",
                        userId);
                }
                finally
                {
                    _queue.MarkDone(userId);
                }
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Graceful shutdown.
        }

        _logger.LogInformation("Face Clustering Background Service stopped");
    }
}
