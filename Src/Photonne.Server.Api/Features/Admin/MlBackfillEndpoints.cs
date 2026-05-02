using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>Snapshot of how many assets remain for a given ML job type.
/// <c>Unprocessed</c> = images with no completion AND no Pending/Processing job
/// (the count the backfill loop will encode). <c>InQueue</c> = assets that
/// already have a Pending/Processing job waiting on the ML processor.</summary>
public record PendingCountResponse(int Unprocessed, int InQueue);

/// <summary>Shared implementation for the per-job-type backfill endpoints.
/// Selects image assets whose <c>*CompletedAt</c> is null (when
/// <see cref="BackfillRequest.OnlyMissing"/> is true, the default) and enqueues
/// the requested job type. Deduplication of Pending/Processing jobs lives in
/// <see cref="IMlJobService.EnqueueMlJobAsync"/>.</summary>
internal static class MlBackfillRunner
{
    public const string BackfillBatchSizeSettingKey = "TaskSettings.BackfillBatchSize";
    public const int DefaultBackfillBatchSize = 500;
    public const int MinBackfillBatchSize = 1;
    public const int MaxBackfillBatchSize = 5000;

    public static async Task<IResult> RunAsync(
        ApplicationDbContext db,
        IMlJobService mlJobs,
        SettingsService settings,
        MlJobType jobType,
        BackfillRequest? body,
        CancellationToken ct,
        Guid? ownerScope = null)
    {
        var batchSize = Math.Clamp(
            body?.BatchSize ?? await ReadGlobalBatchSizeAsync(settings),
            MinBackfillBatchSize,
            MaxBackfillBatchSize);
        var onlyMissing = body?.OnlyMissing ?? true;

        var query = BuildQuery(db, jobType, onlyMissing, ownerScope);

        var total = await query.CountAsync(ct);

        var ids = await query
            .OrderBy(a => a.ScannedAt)
            .Take(batchSize)
            .Select(a => a.Id)
            .ToListAsync(ct);

        var enqueued = 0;
        foreach (var assetId in ids)
        {
            await mlJobs.EnqueueMlJobAsync(assetId, jobType, ct);
            enqueued++;
        }

        return Results.Ok(new BackfillResponse(enqueued, total));
    }

    /// <summary>Returns how many image assets are still missing the given ML job
    /// completion (split by whether they're already enqueued or not). Used by
    /// the admin UI so the operator can tell "all done" from "all in queue".
    /// When <paramref name="ownerScope"/> is set, both numbers are restricted to
    /// assets owned by that user.</summary>
    public static async Task<IResult> GetPendingCountAsync(
        ApplicationDbContext db,
        MlJobType jobType,
        CancellationToken ct,
        Guid? ownerScope = null)
    {
        var unprocessed = await BuildQuery(db, jobType, onlyMissing: true, ownerScope).CountAsync(ct);

        var inQueueQuery = db.AssetMlJobs.AsNoTracking()
            .Where(j => j.JobType == jobType
                && (j.Status == MlJobStatus.Pending || j.Status == MlJobStatus.Processing));
        if (ownerScope.HasValue)
        {
            inQueueQuery = inQueueQuery.Where(j => j.Asset.OwnerId == ownerScope.Value);
        }
        var inQueue = await inQueueQuery.CountAsync(ct);

        return Results.Ok(new PendingCountResponse(unprocessed, inQueue));
    }

    private static IQueryable<Asset> BuildQuery(
        ApplicationDbContext db,
        MlJobType jobType,
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
            // processor finishes them) and EnqueueMlJobAsync would dedup them all,
            // making the loop terminate after one batch.
            query = query.Where(a => !db.AssetMlJobs.Any(j =>
                j.AssetId == a.Id &&
                j.JobType == jobType &&
                (j.Status == MlJobStatus.Pending || j.Status == MlJobStatus.Processing)));
        }

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
            [FromServices] IMlJobService mlJobs,
            [FromServices] SettingsService settings,
            [FromBody] BackfillRequest? body,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, MlJobType.ObjectDetection, body, ct));

        group.MapGet("/object-detection/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, MlJobType.ObjectDetection, ct));
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
            [FromServices] IMlJobService mlJobs,
            [FromServices] SettingsService settings,
            [FromBody] BackfillRequest? body,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, MlJobType.SceneClassification, body, ct));

        group.MapGet("/scene-classification/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, MlJobType.SceneClassification, ct));
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
            [FromServices] IMlJobService mlJobs,
            [FromServices] SettingsService settings,
            [FromBody] BackfillRequest? body,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, MlJobType.TextRecognition, body, ct));

        group.MapGet("/text-recognition/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, MlJobType.TextRecognition, ct));
    }
}
