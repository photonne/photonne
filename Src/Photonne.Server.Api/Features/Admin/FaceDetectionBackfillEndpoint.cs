using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

public record BackfillRequest(int? BatchSize, bool? OnlyMissing);

public record BackfillResponse(int Enqueued, int Total);

/// <summary>Admin-only: enqueues FaceDetection ML jobs for all existing image
/// assets that haven't been processed yet. The MlJobProcessorService picks them
/// up at its normal cadence; deduplication in MlJobService prevents duplicate
/// pending jobs.</summary>
public class FaceDetectionBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/face-detection/backfill", Handle);
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
            query = query.Where(a => a.FaceDetectionCompletedAt == null);
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
            await mlJobs.EnqueueMlJobAsync(assetId, MlJobType.FaceDetection, ct);
            enqueued++;
        }

        return Results.Ok(new BackfillResponse(enqueued, total));
    }
}
