using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>Snapshot of how many assets remain for a given ML job type.
/// <c>Unprocessed</c> = images with no completion AND no Pending/Processing job
/// (the count the backfill loop will encode). <c>InQueue</c> = assets that
/// already have a Pending/Processing job waiting on the ML processor.
/// <c>Completed</c> = images that already have a non-null <c>*CompletedAt</c>
/// for this task type — drives a determinate progress bar in the admin
/// dashboard (<c>completed / (completed + inQueue)</c> = "of what's in
/// motion, fraction done").
///
/// <c>Retrying</c> and <c>Failed</c> exist because the three numbers above
/// cannot tell a working queue from a thrashing one. An ML service that is down
/// keeps assets cycling Failed → (backoff) → Pending → Processing → Failed, so
/// <c>InQueue</c> stays above zero indefinitely and the admin screen reports
/// steady progress on work that is going nowhere. <c>Retrying</c> is the
/// assets waiting on a backoff window; <c>Failed</c> is the ones that exhausted
/// their attempts and now need the failures registry — they are excluded from
/// <c>Unprocessed</c>, which is why a library with thousands of unanalysed
/// photos can otherwise report nothing left to do.
///
/// The last three answer "is it doing anything right now?", which none of the
/// totals can: on a queue of twenty thousand the percentage sits on 0 for
/// minutes whether the workers are busy or dead. <c>Processing</c> is the jobs
/// a worker has claimed this instant, <c>LastCompletedAt</c> is when one last
/// finished, and <c>CompletedLastMinute</c> is the throughput — which also
/// gives the client an ETA that is honest about the rate the machine actually
/// sustains, rather than one extrapolated from two polls.</summary>
public record PendingCountResponse(
    int Unprocessed,
    int InQueue,
    int Completed,
    int Retrying = 0,
    int Failed = 0,
    int Processing = 0,
    DateTime? LastCompletedAt = null,
    int CompletedLastMinute = 0);

/// <summary>Distinct count of image assets that are missing at least one ML
/// enrichment (face / object / scene / OCR / embedding). Used by the admin
/// dashboard so the "still to analyze" headline is honest — summing the five
/// per-type counts would double-count assets that are missing several ML
/// completions at once.</summary>
public record MlPendingTotalResponse(int Count);

/// <summary>Result of clearing the queue for an ML task type.
/// <c>Deleted</c> is the number of <c>AssetEnrichmentTasks</c> rows that were
/// removed — the Pending ones, plus the Failed ones still holding a retry slot,
/// which would otherwise march straight back into Pending at the end of their
/// backoff window and undo the cancellation a few minutes later.
/// <c>StillProcessing</c> is what the workers had already claimed: we can't
/// abort an in-flight inference, and saying so is the difference between a
/// button that looks broken and one that explains itself.</summary>
public record CancelQueueResponse(int Deleted, int StillProcessing = 0);

/// <summary>Shared implementation for the per-job-type backfill endpoints.
/// Selects image assets whose <c>*CompletedAt</c> is null (when
/// <see cref="BackfillRequest.OnlyMissing"/> is true, the default) and enqueues
/// the requested job type in one bulk call. Deduplication of Pending/Processing
/// jobs lives in <see cref="IEnrichmentService.EnqueueManyAsync"/>.</summary>
internal static class MlBackfillRunner
{
    public const string BackfillBatchSizeSettingKey = "TaskSettings.BackfillBatchSize";
    public const int DefaultBackfillBatchSize = 500;
    public const int MinBackfillBatchSize = 1;
    public const int MaxBackfillBatchSize = 5000;

    public static async Task<IResult> RunAsync(
        ApplicationDbContext db,
        IEnrichmentService mlJobs,
        SettingsService settings,
        AssetEnrichmentType jobType,
        BackfillRequest? body,
        CancellationToken ct,
        Guid? ownerScope = null,
        INotificationService? notifications = null,
        Guid? triggeredBy = null,
        MlEnablement? enablement = null)
    {
        // Refuse rather than queue work the worker will discard. A disabled
        // model's service returns early without stamping *CompletedAt, so the
        // assets come straight back into the unprocessed pool: the caller sees a
        // queue that fills, drains, and leaves exactly as much to do as before.
        if (enablement is not null && !await enablement.IsEnabledAsync(jobType))
        {
            return Results.Json(
                new { error = $"El análisis «{JobTypeLabel(jobType)}» está desactivado en Ajustes. Actívalo antes de lanzar el backfill." },
                statusCode: StatusCodes.Status409Conflict);
        }

        // "Encolar todo" is one request, not a client-side loop over batches:
        // the loop needs the pool to shrink on every pass to terminate, and
        // there are real states where it doesn't.
        var queueEverything = body?.All == true;
        int? batchSize = queueEverything
            ? null
            : Math.Clamp(
                body?.BatchSize ?? await ReadGlobalBatchSizeAsync(settings),
                MinBackfillBatchSize,
                MaxBackfillBatchSize);
        var onlyMissing = body?.OnlyMissing ?? true;

        try
        {
            var stopwatch = System.Diagnostics.Stopwatch.StartNew();
            var result = await EnqueueAsync(db, mlJobs, jobType, onlyMissing, batchSize, ownerScope, ct);
            result = result with { ElapsedMs = stopwatch.ElapsedMilliseconds };

            if (notifications is not null && triggeredBy is { } uid && uid != Guid.Empty && result.Enqueued > 0)
            {
                var label = JobTypeLabel(jobType);
                await notifications.CreateAsync(uid, NotificationType.JobCompleted,
                    $"Backfill encolado: {label}",
                    $"Encolados {result.Enqueued} de {result.Total} asset(s) pendientes para {label}. El procesador ML los irá completando en segundo plano.");
            }

            return Results.Ok(result);
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            if (notifications is not null && triggeredBy is { } uid && uid != Guid.Empty)
            {
                var label = JobTypeLabel(jobType);
                var reason = ex.Message.Length > 200 ? ex.Message[..200] + "…" : ex.Message;
                await notifications.CreateAsync(uid, NotificationType.JobFailed,
                    $"Backfill fallido: {label}",
                    $"No se pudo encolar el backfill de {label}: {reason}");
            }
            throw;
        }
    }

    /// <summary>Core enqueue loop used by both the admin backfill endpoints and
    /// the nightly scheduler. Pass <paramref name="batchSize"/> = null to enqueue
    /// every matching asset (used by the nightly path; the processor drains the
    /// queue at its own pace).</summary>
    public static async Task<BackfillResponse> EnqueueAsync(
        ApplicationDbContext db,
        IEnrichmentService mlJobs,
        AssetEnrichmentType jobType,
        bool onlyMissing,
        int? batchSize,
        Guid? ownerScope,
        CancellationToken ct)
    {
        var query = BuildQuery(db, jobType, onlyMissing, ownerScope);
        var total = await query.CountAsync(ct);

        IQueryable<Asset> ordered = query.OrderBy(a => a.ScannedAt);
        if (batchSize.HasValue)
            ordered = ordered.Take(batchSize.Value);

        var ids = await ordered.Select(a => a.Id).ToListAsync(ct);
        await mlJobs.EnqueueManyAsync(ids, jobType, ct);
        // Every id here came out of a query that already excluded the assets
        // with a live row, so "enqueued" is the slice we took — the bulk path's
        // own created-count would under-report the ones it merely re-pushed.
        return new BackfillResponse(ids.Count, total);
    }

    public static string JobTypeLabel(AssetEnrichmentType type) => type switch
    {
        AssetEnrichmentType.FaceRecognition     => "reconocimiento facial",
        AssetEnrichmentType.ObjectDetection     => "detección de objetos",
        AssetEnrichmentType.SceneClassification => "clasificación de escenas",
        AssetEnrichmentType.TextRecognition     => "reconocimiento de texto",
        AssetEnrichmentType.ImageEmbedding      => "embeddings de imagen",
        _                             => type.ToString()
    };

    /// <summary>Returns how many image assets are still missing the given ML job
    /// completion (split by whether they're already enqueued or not). Used by
    /// the admin UI so the operator can tell "all done" from "all in queue".
    /// When <paramref name="ownerScope"/> is set, both numbers are restricted to
    /// assets owned by that user.</summary>
    public static async Task<IResult> GetPendingCountAsync(
        ApplicationDbContext db,
        AssetEnrichmentType jobType,
        CancellationToken ct,
        Guid? ownerScope = null)
    {
        var unprocessed = await BuildQuery(db, jobType, onlyMissing: true, ownerScope).CountAsync(ct);

        var inQueueQuery = db.AssetEnrichmentTasks.AsNoTracking()
            .Where(j => j.TaskType == jobType
                && (j.Status == EnrichmentStatus.Pending || j.Status == EnrichmentStatus.Processing));
        if (ownerScope.HasValue)
        {
            inQueueQuery = inQueueQuery.Where(j => j.Asset.OwnerId == ownerScope.Value);
        }
        var inQueue = await inQueueQuery.CountAsync(ct);

        var completedQuery = db.Assets.AsNoTracking()
            .Where(a => a.Type == AssetType.Image && a.DeletedAt == null && !a.IsFileMissing)
            .Where(MediaRecognitionService.CompletedFilter(jobType));
        if (ownerScope.HasValue)
        {
            completedQuery = completedQuery.Where(a => a.OwnerId == ownerScope.Value);
        }
        var completed = await completedQuery.CountAsync(ct);

        // Counted per asset, not per row: a repeatedly-failing asset accumulates
        // one Failed row per attempt, and "1.204 fotos con errores" is the
        // number an operator can act on — "5.117 intentos fallidos" isn't.
        var failedQuery = db.AssetEnrichmentTasks.AsNoTracking()
            .Where(j => j.TaskType == jobType && j.Status == EnrichmentStatus.Failed);
        if (ownerScope.HasValue)
        {
            failedQuery = failedQuery.Where(j => j.Asset.OwnerId == ownerScope.Value);
        }
        var failedGroups = await failedQuery
            .GroupBy(j => j.NextRetryAt == null)
            .Select(g => new { Permanent = g.Key, Assets = g.Select(j => j.AssetId).Distinct().Count() })
            .ToListAsync(ct);

        var retrying = failedGroups.FirstOrDefault(g => !g.Permanent)?.Assets ?? 0;
        var failed = failedGroups.FirstOrDefault(g => g.Permanent)?.Assets ?? 0;

        // Liveness. All three sit on the (TaskType, Status, CompletedAt) index,
        // so polling them every few seconds costs an index probe, not a walk
        // over one row per attempt ever made.
        var processingQuery = db.AssetEnrichmentTasks.AsNoTracking()
            .Where(j => j.TaskType == jobType && j.Status == EnrichmentStatus.Processing);
        if (ownerScope.HasValue)
        {
            processingQuery = processingQuery.Where(j => j.Asset.OwnerId == ownerScope.Value);
        }
        var processing = await processingQuery.CountAsync(ct);

        var completedRows = db.AssetEnrichmentTasks.AsNoTracking()
            .Where(j => j.TaskType == jobType && j.Status == EnrichmentStatus.Completed && j.CompletedAt != null);
        if (ownerScope.HasValue)
        {
            completedRows = completedRows.Where(j => j.Asset.OwnerId == ownerScope.Value);
        }
        var lastCompletedAt = await completedRows.MaxAsync(j => j.CompletedAt, ct);
        var minuteAgo = DateTime.UtcNow.AddMinutes(-1);
        var completedLastMinute = await completedRows.CountAsync(j => j.CompletedAt >= minuteAgo, ct);

        return Results.Ok(new PendingCountResponse(
            unprocessed, inQueue, completed, retrying, failed,
            processing, lastCompletedAt, completedLastMinute));
    }

    /// <summary>How many distinct image assets are missing at least one ML
    /// completion. Cheaper than fetching 5 per-type counts client-side and
    /// summing them (which double-counts), and matches what the dashboard's
    /// "N assets sin analizar" headline actually means.</summary>
    public static async Task<IResult> GetAnyMlMissingCountAsync(
        ApplicationDbContext db,
        CancellationToken ct)
    {
        var count = await db.Assets.AsNoTracking()
            .Where(a => a.Type == AssetType.Image && a.DeletedAt == null && !a.IsFileMissing)
            .Where(a => a.FaceRecognitionCompletedAt == null
                     || a.ObjectDetectionCompletedAt == null
                     || a.SceneClassificationCompletedAt == null
                     || a.TextRecognitionCompletedAt == null
                     || a.ImageEmbeddingCompletedAt == null)
            .CountAsync(ct);
        return Results.Ok(new MlPendingTotalResponse(count));
    }

    /// <summary>Empties the queue for the given task type: the
    /// <see cref="EnrichmentStatus.Pending"/> rows, and the
    /// <see cref="EnrichmentStatus.Failed"/> rows that still have a retry
    /// scheduled. Dropping only the Pending slice used to make this button look
    /// broken — <see cref="EnrichmentWorker"/> walks the due Failed rows back
    /// into Pending every five minutes, so a cancelled queue refilled itself
    /// before the admin had finished reading the screen.
    ///
    /// Jobs already <see cref="EnrichmentStatus.Processing"/> are left alone —
    /// the worker that claimed one will finish (or fail) it; there's no safe way
    /// to abort an in-flight inference. They're counted and returned instead, so
    /// the client can say "quedan 3 en curso" rather than appear to do nothing.
    /// Permanently failed rows stay put: they belong to the failures registry,
    /// which is where they get retried or suppressed one by one.</summary>
    public static async Task<IResult> CancelQueueAsync(
        ApplicationDbContext db,
        AssetEnrichmentType jobType,
        CancellationToken ct)
    {
        var deleted = await db.AssetEnrichmentTasks
            .Where(j => j.TaskType == jobType
                && (j.Status == EnrichmentStatus.Pending
                    || (j.Status == EnrichmentStatus.Failed && j.NextRetryAt != null)))
            .ExecuteDeleteAsync(ct);

        var stillProcessing = await db.AssetEnrichmentTasks
            .AsNoTracking()
            .CountAsync(j => j.TaskType == jobType && j.Status == EnrichmentStatus.Processing, ct);

        return Results.Ok(new CancelQueueResponse(deleted, stillProcessing));
    }

    private static IQueryable<Asset> BuildQuery(
        ApplicationDbContext db,
        AssetEnrichmentType jobType,
        bool onlyMissing,
        Guid? ownerScope = null)
    {
        var query = db.Assets.AsNoTracking()
            .Where(a => a.Type == AssetType.Image && a.DeletedAt == null && !a.IsFileMissing);

        if (ownerScope.HasValue)
        {
            query = query.Where(a => a.OwnerId == ownerScope.Value);
        }

        if (onlyMissing)
        {
            query = query.Where(MediaRecognitionService.MissingCompletionFilter(jobType));
            // Exclude assets that already have a Pending/Processing job of the same
            // type. Otherwise iterating the backfill in a loop would keep re-fetching
            // the same first N IDs (their *CompletedAt is still null until the
            // processor finishes them) and EnqueueAsync would dedup them all,
            // making the loop terminate after one batch.
            // Also exclude assets whose latest attempt exhausted the retry
            // backoff (Failed with NextRetryAt null): EnqueueAsync mints a
            // fresh row per call, so without this gate the nightly backfill
            // would grant a poisoned asset 5 new attempts every night, forever.
            query = query.Where(a => !db.AssetEnrichmentTasks.Any(j =>
                j.AssetId == a.Id &&
                j.TaskType == jobType &&
                (j.Status == EnrichmentStatus.Pending ||
                 j.Status == EnrichmentStatus.Processing ||
                 (j.Status == EnrichmentStatus.Failed && j.NextRetryAt == null))));
        }

        // Dismissed assets stay out even on an explicit reprocess-all: the
        // admin said "don't try this one again" and only the failures
        // registry's per-asset retry overrides that.
        query = query.Where(a => !db.AssetEnrichmentTasks.Any(j =>
            j.AssetId == a.Id &&
            j.TaskType == jobType &&
            j.Status == EnrichmentStatus.Suppressed));

        return query;
    }

    private static async Task<int> ReadGlobalBatchSizeAsync(SettingsService settings)
    {
        var raw = await settings.GetSettingAsync(BackfillBatchSizeSettingKey, Guid.Empty, DefaultBackfillBatchSize.ToString());
        return int.TryParse(raw, out var v) ? v : DefaultBackfillBatchSize;
    }
}

/// <summary>Admin-only: enqueues ObjectDetection ML jobs for image assets that
/// haven't been processed yet. Mirrors the FaceRecognition backfill so admins
/// can run object recognition over the existing library after enabling it.</summary>
public class ObjectDetectionBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/object-detection/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IEnrichmentService mlJobs,
            [FromServices] SettingsService settings,
            [FromServices] INotificationService notifications,
            [FromServices] MlEnablement enablement,
            [FromBody] BackfillRequest? body,
            HttpContext http,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, AssetEnrichmentType.ObjectDetection, body, ct, notifications: notifications, triggeredBy: AdminEndpointHelpers.GetUserId(http), enablement: enablement));

        group.MapGet("/object-detection/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, AssetEnrichmentType.ObjectDetection, ct));
    }
}

/// <summary>Admin-only: enqueues SceneClassification ML jobs for image assets
/// that haven't been processed yet.</summary>
public class SceneClassificationBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/scene-classification/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IEnrichmentService mlJobs,
            [FromServices] SettingsService settings,
            [FromServices] INotificationService notifications,
            [FromServices] MlEnablement enablement,
            [FromBody] BackfillRequest? body,
            HttpContext http,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, AssetEnrichmentType.SceneClassification, body, ct, notifications: notifications, triggeredBy: AdminEndpointHelpers.GetUserId(http), enablement: enablement));

        group.MapGet("/scene-classification/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, AssetEnrichmentType.SceneClassification, ct));
    }
}

/// <summary>Admin-only: enqueues TextRecognition (OCR) ML jobs for image assets
/// that haven't been processed yet.</summary>
public class TextRecognitionBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/text-recognition/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IEnrichmentService mlJobs,
            [FromServices] SettingsService settings,
            [FromServices] INotificationService notifications,
            [FromServices] MlEnablement enablement,
            [FromBody] BackfillRequest? body,
            HttpContext http,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, AssetEnrichmentType.TextRecognition, body, ct, notifications: notifications, triggeredBy: AdminEndpointHelpers.GetUserId(http), enablement: enablement));

        group.MapGet("/text-recognition/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, AssetEnrichmentType.TextRecognition, ct));
    }
}

/// <summary>Admin-only: enqueues ImageEmbedding (CLIP) ML jobs for image assets
/// that haven't been processed yet. Required after enabling semantic search on
/// an existing library, and again whenever the embedding model is swapped (the
/// processor will re-encode rows whose stored ModelVersion no longer matches).</summary>
public class ImageEmbeddingBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/image-embedding/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IEnrichmentService mlJobs,
            [FromServices] SettingsService settings,
            [FromServices] INotificationService notifications,
            [FromServices] MlEnablement enablement,
            [FromBody] BackfillRequest? body,
            HttpContext http,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, AssetEnrichmentType.ImageEmbedding, body, ct, notifications: notifications, triggeredBy: AdminEndpointHelpers.GetUserId(http), enablement: enablement));

        group.MapGet("/image-embedding/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, AssetEnrichmentType.ImageEmbedding, ct));
    }
}

/// <summary>Admin-only: cross-cutting ML maintenance endpoints — the
/// dashboard headline count and the per-type queue-clearing action.
/// Both live outside the per-feature endpoint classes because they
/// either span all five enrichment types or are best handled by a
/// table-name → enum lookup in one place.</summary>
public class MlOverviewEndpoint : IEndpoint
{
    private static readonly Dictionary<string, AssetEnrichmentType> KindRoutes = new(StringComparer.OrdinalIgnoreCase)
    {
        ["face-recognition"]      = AssetEnrichmentType.FaceRecognition,
        ["object-detection"]      = AssetEnrichmentType.ObjectDetection,
        ["scene-classification"]  = AssetEnrichmentType.SceneClassification,
        ["text-recognition"]      = AssetEnrichmentType.TextRecognition,
        ["image-embedding"]       = AssetEnrichmentType.ImageEmbedding,
        // Not an ML model, but it rides the same enrichment queue and the
        // admin hub offers it the same cancel button — which answered 404
        // while this line was missing.
        ["media-recognition"]     = AssetEnrichmentType.MediaRecognition,
    };

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("/ml-pending-total", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetAnyMlMissingCountAsync(db, ct));

        // Single handler for the five `/{kind}/queue` cancel routes —
        // saves duplicating the per-feature endpoint class for an
        // action that doesn't otherwise vary across task types.
        group.MapDelete("/{kind}/queue", (
            string kind,
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) =>
        {
            if (!KindRoutes.TryGetValue(kind, out var jobType))
                return Task.FromResult<IResult>(Results.NotFound());
            return MlBackfillRunner.CancelQueueAsync(db, jobType, ct)
                .ContinueWith(t => t.Result);
        });
    }
}
