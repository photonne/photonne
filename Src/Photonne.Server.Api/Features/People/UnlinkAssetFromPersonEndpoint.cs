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

        var faces = await db.Faces.Include(f => f.Asset)
            .Where(f => f.PersonId == personId && f.AssetId == assetId && f.Asset.OwnerId == userId)
            .ToListAsync(ct);

        if (faces.Count == 0) return Results.Ok(new UnlinkAssetResponse(0));

        foreach (var f in faces)
        {
            f.PersonId = null;
            f.IsManuallyAssigned = true; // prevents the online clusterer from re-attaching
            f.IsRejected = false;
        }
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsAsync(userId, ct);
        await clustering.CleanupEmptyPersonsAsync(userId, ct);

        return Results.Ok(new UnlinkAssetResponse(faces.Count));
    }
}
