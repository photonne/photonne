using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.VideoSummaries;

/// <summary>
/// Renders requested summaries one at a time. One is enough: rendering keeps
/// every core busy for minutes, and two at once would only make both slower
/// while starving the rest of the server.
///
/// On start it re-queues whatever a previous process left pending or
/// half-rendered (a restart mid-render), oldest request first.
/// </summary>
public sealed class VideoSummaryWorker : BackgroundService
{
    private readonly IServiceProvider _services;
    private readonly VideoSummaryQueue _queue;
    private readonly ILogger<VideoSummaryWorker> _logger;

    public VideoSummaryWorker(IServiceProvider services, VideoSummaryQueue queue, ILogger<VideoSummaryWorker> logger)
    {
        _services = services;
        _queue = queue;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await RequeueUnfinishedAsync(stoppingToken);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            _logger.LogWarning(ex, "Could not re-queue unfinished video summaries");
        }

        try
        {
            await foreach (var summaryId in _queue.Reader.ReadAllAsync(stoppingToken))
            {
                try
                {
                    using var scope = _services.CreateScope();
                    var generator = scope.ServiceProvider.GetRequiredService<VideoSummaryGenerator>();
                    await generator.RunAsync(summaryId, stoppingToken);
                }
                catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                {
                    throw;
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Video summary {SummaryId} crashed the worker loop", summaryId);
                }
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Shutdown.
        }
    }

    private async Task RequeueUnfinishedAsync(CancellationToken ct)
    {
        using var scope = _services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

        await db.VideoSummaries
            .Where(s => s.Status == VideoSummaryStatus.Running)
            .ExecuteUpdateAsync(u => u
                .SetProperty(s => s.Status, VideoSummaryStatus.Pending)
                .SetProperty(s => s.Progress, 0), ct);

        var pending = await db.VideoSummaries
            .Where(s => s.Status == VideoSummaryStatus.Pending)
            .OrderBy(s => s.RequestedAt)
            .Select(s => s.Id)
            .ToListAsync(ct);
        foreach (var id in pending) _queue.Enqueue(id);

        await scope.ServiceProvider.GetRequiredService<Music.SummaryMusicStore>().SweepAsync(db, ct);
    }
}
