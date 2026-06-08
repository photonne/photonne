using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>Snapshot for the MediaRecognition maintenance action. <c>Unprocessed</c> =
/// media assets a run would (re)enqueue right now, i.e. every media asset that doesn't
/// already have a Pending/Processing MediaRecognition job. <c>InQueue</c> = media assets
/// with a MediaRecognition job already waiting.</summary>
public record MediaRecognitionPendingResponse(int Unprocessed, int InQueue);

/// <summary>Admin-only re-runnable maintenance action that (re)runs MediaRecognition
/// over the existing library so the still/motion halves of Live Photos get tagged
/// LivePhoto/MotionPhotoPart (and the .mov half drops out of the timeline and folder
/// listings).
///
/// This is NOT an ML backfill: there is no per-asset <c>*CompletedAt</c> marker, and the
/// result depends on sibling files on disk that can change between runs, so every media
/// asset (image AND video — a Live Photo is a still plus a clip) is always a valid
/// candidate and the action is meant to be re-run as maintenance after moving files,
/// adding Live Photos, or reindexing. The task is cheap and idempotent — the worker
/// deletes existing tags and recomputes from disk siblings (<see cref="MediaRecognitionService"/>)
/// — and a run only skips assets that already have a MediaRecognition job queued (plain
/// de-dup), so iterating in batches converges.</summary>
public class MediaRecognitionBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/media-recognition/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IEnrichmentService jobs,
            [FromServices] SettingsService settings,
            [FromServices] INotificationService notifications,
            [FromBody] BackfillRequest? body,
            HttpContext http,
            CancellationToken ct) => RunAsync(db, jobs, settings, body, notifications, AdminEndpointHelpers.GetUserId(http), ct));

        group.MapGet("/media-recognition/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => GetPendingCountAsync(db, ct));
    }

    private static async Task<IResult> RunAsync(
        ApplicationDbContext db,
        IEnrichmentService jobs,
        SettingsService settings,
        BackfillRequest? body,
        INotificationService notifications,
        Guid triggeredBy,
        CancellationToken ct)
    {
        var batchSize = Math.Clamp(
            body?.BatchSize ?? await ReadGlobalBatchSizeAsync(settings),
            MlBackfillRunner.MinBackfillBatchSize,
            MlBackfillRunner.MaxBackfillBatchSize);
        var onlyMissing = body?.OnlyMissing ?? true;

        try
        {
            var query = BuildQuery(db, onlyMissing);
            var total = await query.CountAsync(ct);

            var ids = await query
                .OrderBy(a => a.ScannedAt)
                .Take(batchSize)
                .Select(a => a.Id)
                .ToListAsync(ct);

            var enqueued = 0;
            foreach (var id in ids)
            {
                if (ct.IsCancellationRequested) break;
                await jobs.EnqueueAsync(id, AssetEnrichmentType.MediaRecognition, ct);
                enqueued++;
            }

            if (triggeredBy != Guid.Empty && enqueued > 0)
            {
                await notifications.CreateAsync(triggeredBy, NotificationType.JobCompleted,
                    "Backfill encolado: reconocimiento de medios",
                    $"Encolados {enqueued} de {total} asset(s) para emparejar Live Photos. El procesador los irá completando en segundo plano.");
            }

            return Results.Ok(new BackfillResponse(enqueued, total));
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobFailed,
                    "Backfill fallido: reconocimiento de medios",
                    $"No se pudo encolar el backfill: {AdminEndpointHelpers.Truncate(ex.Message, 200)}");
            throw;
        }
    }

    private static async Task<IResult> GetPendingCountAsync(ApplicationDbContext db, CancellationToken ct)
    {
        var unprocessed = await BuildQuery(db, onlyMissing: true).CountAsync(ct);

        var inQueue = await db.AssetEnrichmentTasks.AsNoTracking()
            .Where(j => j.TaskType == AssetEnrichmentType.MediaRecognition
                && (j.Status == EnrichmentStatus.Pending || j.Status == EnrichmentStatus.Processing))
            .CountAsync(ct);

        return Results.Ok(new MediaRecognitionPendingResponse(unprocessed, inQueue));
    }

    private static IQueryable<Asset> BuildQuery(ApplicationDbContext db, bool onlyMissing)
    {
        // This is a re-runnable maintenance action ("re-pair all media"), not an
        // ML backfill: MediaRecognition has no per-asset completion marker, and its
        // result depends on sibling files on disk that can change between runs, so
        // every media asset is always a valid candidate. We only ever exclude assets
        // that already have a MediaRecognition job waiting — that's plain de-dup so
        // iterating the backfill in batches converges (each batch queues a slice,
        // which the next pass then skips) instead of re-fetching the same IDs. The
        // `onlyMissing` flag is kept for signature parity with the ML backfills but
        // doesn't change the candidate set here.
        _ = onlyMissing;
        return db.Assets.AsNoTracking()
            .Where(a => (a.Type == AssetType.Image || a.Type == AssetType.Video)
                     && a.DeletedAt == null && !a.IsFileMissing)
            .Where(a => !db.AssetEnrichmentTasks.Any(j =>
                j.AssetId == a.Id &&
                j.TaskType == AssetEnrichmentType.MediaRecognition &&
                (j.Status == EnrichmentStatus.Pending || j.Status == EnrichmentStatus.Processing)));
    }

    private static async Task<int> ReadGlobalBatchSizeAsync(SettingsService settings)
    {
        var raw = await settings.GetSettingAsync(
            MlBackfillRunner.BackfillBatchSizeSettingKey, Guid.Empty,
            MlBackfillRunner.DefaultBackfillBatchSize.ToString());
        return int.TryParse(raw, out var v) ? v : MlBackfillRunner.DefaultBackfillBatchSize;
    }
}
