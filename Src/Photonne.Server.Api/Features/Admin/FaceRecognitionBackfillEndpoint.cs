using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Features.Admin;

public record BackfillRequest(int? BatchSize, bool? OnlyMissing);

public record BackfillResponse(int Enqueued, int Total);

public record GlobalReclusterResponse(int OwnersProcessed, int PersonsCreated);

/// <summary>Admin-only: enqueues FaceRecognition ML jobs for all existing image
/// assets that haven't been processed yet. The MlJobProcessorService picks them
/// up at its normal cadence; deduplication in MlJobService prevents duplicate
/// pending jobs.</summary>
public class FaceRecognitionBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/face-recognition/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IMlJobService mlJobs,
            [FromServices] SettingsService settings,
            [FromBody] BackfillRequest? body,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, MlJobType.FaceRecognition, body, ct));

        group.MapGet("/face-recognition/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, MlJobType.FaceRecognition, ct));
    }
}

/// <summary>Admin-only: re-runs the batch face clustering across every owner
/// that has orphan faces. Useful after a backfill so newly-detected faces
/// consolidate into Persons without waiting for the nightly job.</summary>
public class FaceClusteringRunGlobalEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/face-clustering/run", Handle);
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] FaceClusteringService clustering,
        CancellationToken ct)
    {
        // Identity is per-user (UserFaceAssignment) since the split. The
        // legacy "global recluster" passes face filtering had no per-user
        // notion; we now interpret it as "for every user that owns assets
        // with detections, run their clustering once". Users with shared-
        // only access cluster lazily on /people via EnsureUpToDateForUserAsync,
        // so they don't need a global pass.
        var ownerIds = await db.Faces.AsNoTracking()
            .Select(f => f.Asset.OwnerId)
            .Where(id => id != null)
            .Distinct()
            .ToListAsync(ct);

        var ownersProcessed = 0;
        var personsCreated = 0;
        foreach (var oid in ownerIds.OfType<Guid>())
        {
            var created = await clustering.RunForUserAsync(oid, ct);
            personsCreated += created;
            ownersProcessed++;
        }

        return Results.Ok(new GlobalReclusterResponse(ownersProcessed, personsCreated));
    }
}
