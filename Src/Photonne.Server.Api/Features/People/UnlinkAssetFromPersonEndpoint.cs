using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Features.People;

public sealed record UnlinkAssetResponse(int FacesDetached);

/// <summary>Detaches every face on a given asset that's currently linked to
/// a given person, leaving them as orphans. Used by the bulk-unassign action
/// on PersonDetail (a person can have several faces in the same photo, so
/// we operate at asset granularity, not face).</summary>
public class UnlinkAssetFromPersonEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/{personId:guid}/assets/{assetId:guid}/unlink", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] FaceClusteringService clustering,
        Guid personId,
        Guid assetId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var person = await db.People.AsNoTracking()
            .FirstOrDefaultAsync(p => p.Id == personId && p.OwnerId == userId, ct);
        if (person == null) return Results.NotFound();

        // Operate on the user's per-face assignments, not the shared Face row.
        // Other users keep their identity for the same face.
        var assignments = await db.UserFaceAssignments
            .Where(uf => uf.UserId == userId
                         && uf.PersonId == personId
                         && uf.Face.AssetId == assetId)
            .ToListAsync(ct);

        if (assignments.Count == 0) return Results.Ok(new UnlinkAssetResponse(0));

        var now = DateTime.UtcNow;
        foreach (var uf in assignments)
        {
            uf.PersonId = null;
            uf.IsManuallyAssigned = true; // prevents the online clusterer from re-attaching
            uf.IsRejected = false;
            uf.UpdatedAt = now;
        }
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsForUserAsync(userId, ct);
        await clustering.CleanupEmptyPersonsAsync(userId, ct);

        return Results.Ok(new UnlinkAssetResponse(assignments.Count));
    }
}
