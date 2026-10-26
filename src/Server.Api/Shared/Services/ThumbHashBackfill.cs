using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using SixLabors.ImageSharp;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Gives a ThumbHash to the Small thumbnails made before there were any,
/// from the thumbnail file itself (220 px, cheap to read). One that can't be
/// read is marked with an empty hash so the sweep doesn't retry it forever;
/// the clients treat it as none.
/// </summary>
public sealed class ThumbHashBackfill(ApplicationDbContext db, ILogger<ThumbHashBackfill> logger)
{
    /// <summary>Fills up to <paramref name="batchSize"/> thumbnails; returns how many it looked at.</summary>
    public async Task<int> RunBatchAsync(int batchSize, CancellationToken ct)
    {
        var thumbnails = await db.AssetThumbnails
            .Where(t => t.Size == ThumbnailSize.Small && t.ThumbHash == null)
            .OrderBy(t => t.Id)
            .Take(batchSize)
            .ToListAsync(ct);

        foreach (var thumbnail in thumbnails)
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                using var image = await Image.LoadAsync(thumbnail.FilePath, ct);
                thumbnail.ThumbHash = ThumbnailGeneratorService.ComputeThumbHash(image) ?? string.Empty;
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception ex)
            {
                logger.LogDebug(ex, "No ThumbHash for thumbnail {Id}", thumbnail.Id);
                thumbnail.ThumbHash = string.Empty;
            }
        }

        await db.SaveChangesAsync(ct);
        return thumbnails.Count;
    }
}

/// <summary>
/// Runs <see cref="ThumbHashBackfill"/> once after startup, batch by batch,
/// until every Small thumbnail has a ThumbHash. New thumbnails get theirs
/// when they are generated, so after the first full pass this finds nothing.
/// </summary>
public sealed class ThumbHashBackfillWorker(IServiceScopeFactory scopeFactory, ILogger<ThumbHashBackfillWorker> logger)
    : BackgroundService
{
    private static readonly TimeSpan StartupDelay = TimeSpan.FromMinutes(1);
    private const int BatchSize = 200;

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await Task.Delay(StartupDelay, stoppingToken);
            var total = 0;
            while (true)
            {
                using var scope = scopeFactory.CreateScope();
                var backfill = scope.ServiceProvider.GetRequiredService<ThumbHashBackfill>();
                var done = await backfill.RunBatchAsync(BatchSize, stoppingToken);
                total += done;
                if (done < BatchSize) break;
                // Background work: let indexing and requests go first.
                await Task.Delay(TimeSpan.FromSeconds(1), stoppingToken);
            }
            if (total > 0) logger.LogInformation("ThumbHash backfill done: {Count} thumbnails", total);
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Graceful shutdown; the next start carries on.
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "ThumbHash backfill stopped; it resumes on the next start");
        }
    }
}
