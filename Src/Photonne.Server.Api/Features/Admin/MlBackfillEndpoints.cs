using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

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

        group.MapPost("/object-detection/backfill", Handle);
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] IMlJobService mlJobs,
        [FromBody] BackfillRequest? body,
        CancellationToken ct)
    {
        var batchSize = Math.Clamp(body?.BatchSize ?? 500, 1, 5000);
        var onlyMissing = body?.OnlyMissing ?? true;

        var query = db.Assets.AsNoTracking()
            .Where(a => a.Type == AssetType.Image && a.DeletedAt == null && !a.IsFileMissing);

        if (onlyMissing)
        {
            query = query.Where(a => a.ObjectDetectionCompletedAt == null);
        }

        var total = await query.CountAsync(ct);

        var ids = await query
            .OrderBy(a => a.ScannedAt)
            .Take(batchSize)
            .Select(a => a.Id)
            .ToListAsync(ct);

        var enqueued = 0;
        foreach (var assetId in ids)
        {
            await mlJobs.EnqueueMlJobAsync(assetId, MlJobType.ObjectDetection, ct);
            enqueued++;
        }

        return Results.Ok(new BackfillResponse(enqueued, total));
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

        group.MapPost("/scene-classification/backfill", Handle);
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] IMlJobService mlJobs,
        [FromBody] BackfillRequest? body,
        CancellationToken ct)
    {
        var batchSize = Math.Clamp(body?.BatchSize ?? 500, 1, 5000);
        var onlyMissing = body?.OnlyMissing ?? true;

        var query = db.Assets.AsNoTracking()
            .Where(a => a.Type == AssetType.Image && a.DeletedAt == null && !a.IsFileMissing);

        if (onlyMissing)
        {
            query = query.Where(a => a.SceneClassificationCompletedAt == null);
        }

        var total = await query.CountAsync(ct);

        var ids = await query
            .OrderBy(a => a.ScannedAt)
            .Take(batchSize)
            .Select(a => a.Id)
            .ToListAsync(ct);

        var enqueued = 0;
        foreach (var assetId in ids)
        {
            await mlJobs.EnqueueMlJobAsync(assetId, MlJobType.SceneClassification, ct);
            enqueued++;
        }

        return Results.Ok(new BackfillResponse(enqueued, total));
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

        group.MapPost("/text-recognition/backfill", Handle);
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] IMlJobService mlJobs,
        [FromBody] BackfillRequest? body,
        CancellationToken ct)
    {
        var batchSize = Math.Clamp(body?.BatchSize ?? 500, 1, 5000);
        var onlyMissing = body?.OnlyMissing ?? true;

        var query = db.Assets.AsNoTracking()
            .Where(a => a.Type == AssetType.Image && a.DeletedAt == null && !a.IsFileMissing);

        if (onlyMissing)
        {
            query = query.Where(a => a.TextRecognitionCompletedAt == null);
        }

        var total = await query.CountAsync(ct);

        var ids = await query
            .OrderBy(a => a.ScannedAt)
            .Take(batchSize)
            .Select(a => a.Id)
            .ToListAsync(ct);

        var enqueued = 0;
        foreach (var assetId in ids)
        {
            await mlJobs.EnqueueMlJobAsync(assetId, MlJobType.TextRecognition, ct);
            enqueued++;
        }

        return Results.Ok(new BackfillResponse(enqueued, total));
    }
}
