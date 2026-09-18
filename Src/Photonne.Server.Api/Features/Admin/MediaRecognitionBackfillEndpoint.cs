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
/// with a MediaRecognition job already waiting. <c>Retrying</c> / <c>Failed</c> mirror
/// <see cref="PendingCountResponse"/> so one client-side shape covers every row of the
/// admin hub — and so a queue that is failing rather than working says so.</summary>
public record MediaRecognitionPendingResponse(
    int Unprocessed,
    int InQueue,
    int Completed = 0,
    int Retrying = 0,
    int Failed = 0);

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
/// de-dup). That de-dup is the only thing bounding a run, which is why a caller must not
/// loop over batches here: the workers return each finished asset to the candidate pool,
/// so "keep asking until there's nothing left" has no end. Ask once, with
/// <see cref="BackfillRequest.All"/> when you mean the whole library.</summary>
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
        // Unlike the ML backfills, this one has no completion marker: an asset
        // that finishes is a candidate again on the next pass. A caller slicing
        // it into batches and looping until "nothing left" therefore never
        // finishes on a library bigger than one batch, because the workers put
        // assets back in the pool as fast as we take them out. So "encolar todo"
        // is one request here too, and the default stays a single slice.
        var queueEverything = body?.All == true;
        int? batchSize = queueEverything
            ? null
            : Math.Clamp(
                body?.BatchSize ?? await ReadGlobalBatchSizeAsync(settings),
                MlBackfillRunner.MinBackfillBatchSize,
                MlBackfillRunner.MaxBackfillBatchSize);
        var onlyMissing = body?.OnlyMissing ?? true;

        try
        {
            var query = BuildQuery(db, onlyMissing);
            var total = await query.CountAsync(ct);

            IQueryable<Asset> ordered = query.OrderBy(a => a.ScannedAt);
            if (batchSize.HasValue) ordered = ordered.Take(batchSize.Value);

            var ids = await ordered.Select(a => a.Id).ToListAsync(ct);
            await jobs.EnqueueManyAsync(ids, AssetEnrichmentType.MediaRecognition, ct);
            var enqueued = ids.Count;

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

        // Same definition as the failures registry, so both say one number.
        var problems = EnrichmentFailureQueries.OpenProblems(db).AsNoTracking()
            .Where(j => j.TaskType == AssetEnrichmentType.MediaRecognition);

        return Results.Ok(new MediaRecognitionPendingResponse(
            unprocessed,
            inQueue,
            Retrying: await problems.Retrying().CountAsync(ct),
            Failed: await problems.Definitive().CountAsync(ct)));
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
                (j.Status == EnrichmentStatus.Pending ||
                 j.Status == EnrichmentStatus.Processing ||
                 j.Status == EnrichmentStatus.Suppressed)));
    }

    private static async Task<int> ReadGlobalBatchSizeAsync(SettingsService settings)
    {
        var raw = await settings.GetSettingAsync(
            MlBackfillRunner.BackfillBatchSizeSettingKey, Guid.Empty,
            MlBackfillRunner.DefaultBackfillBatchSize.ToString());
        return int.TryParse(raw, out var v) ? v : MlBackfillRunner.DefaultBackfillBatchSize;
    }
}
