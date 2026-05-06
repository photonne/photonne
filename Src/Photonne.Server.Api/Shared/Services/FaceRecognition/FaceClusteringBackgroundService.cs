using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

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

                    // Surface the failure to the affected user so they don't see
                    // stale "people" data without any explanation. Best-effort —
                    // never let a notification failure mask the original error.
                    try
                    {
                        using var notifyScope = _serviceProvider.CreateScope();
                        var notifications = notifyScope.ServiceProvider.GetRequiredService<INotificationService>();
                        var reason = ex.Message.Length > 200 ? ex.Message[..200] + "…" : ex.Message;
                        await notifications.CreateAsync(
                            userId,
                            NotificationType.JobFailed,
                            "Error en agrupación de rostros",
                            $"No se pudo agrupar rostros para tu cuenta: {reason}");
                    }
                    catch { /* best effort */ }
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
