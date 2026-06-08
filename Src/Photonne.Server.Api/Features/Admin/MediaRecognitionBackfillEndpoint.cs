using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>Snapshot for the MediaRecognition backfill. <c>Unprocessed</c> =
/// media assets that have no detected media-type tag yet AND no Pending/Processing
/// MediaRecognition job — the rough "never paired" population the backfill targets.
/// <c>InQueue</c> = media assets with a MediaRecognition job already waiting.</summary>
public record MediaRecognitionPendingResponse(int Unprocessed, int InQueue);

/// <summary>Admin-only: (re)runs MediaRecognition over the existing library so the
/// still/motion halves of Live Photos get tagged LivePhoto/MotionPhotoPart (and the
/// .mov half drops out of the timeline and folder listings).
///
/// Unlike the ML backfills this targets BOTH images and videos (a Live Photo is an
/// image still + a video clip) and there is no per-asset <c>*CompletedAt</c> marker,
/// so "missing" is heuristic: an asset with no detected-tag rows is treated as not
/// yet paired. The task itself is cheap and idempotent — the worker deletes existing
/// tags and recomputes from sibling files on disk (<see cref="MediaRecognitionService"/>),
/// and <see cref="IEnrichmentService.EnqueueAsync"/> de-dups any in-flight job — so a
/// reindexed library that was indexed before its clips landed can be paired in one
/// pass without a full re-scan.</summary>
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
        var query = db.Assets.AsNoTracking()
            .Where(a => (a.Type == AssetType.Image || a.Type == AssetType.Video)
                     && a.DeletedAt == null && !a.IsFileMissing);

        if (onlyMissing)
        {
            // No *CompletedAt marker for MediaRecognition, so approximate "not yet
            // paired" as "has no detected-tag rows". This includes plain photos that
            // legitimately produced no tags (cheap to recheck) and, crucially, the
            // untagged still/clip pairs from a library indexed before its clips
            // landed — exactly what the operator wants to pair.
            query = query.Where(a => !a.Tags.Any());

            // Skip assets already waiting on a MediaRecognition job so iterating the
            // backfill in batches makes progress instead of re-fetching the same IDs
            // (their tag state won't change until the processor runs).
            query = query.Where(a => !db.AssetEnrichmentTasks.Any(j =>
                j.AssetId == a.Id &&
                j.TaskType == AssetEnrichmentType.MediaRecognition &&
                (j.Status == EnrichmentStatus.Pending || j.Status == EnrichmentStatus.Processing)));
        }

        return query;
    }

    private static async Task<int> ReadGlobalBatchSizeAsync(SettingsService settings)
    {
        var raw = await settings.GetSettingAsync(
            MlBackfillRunner.BackfillBatchSizeSettingKey, Guid.Empty,
            MlBackfillRunner.DefaultBackfillBatchSize.ToString());
        return int.TryParse(raw, out var v) ? v : MlBackfillRunner.DefaultBackfillBatchSize;
    }
}
