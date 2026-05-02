using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.People;

public sealed record PersonFaceItem(
    Guid Id,
    Guid AssetId,
    float Confidence,
    bool IsManuallyAssigned);

/// <summary>Lists faces attached to a person, ordered by detection confidence
/// desc so the highest-quality candidates surface first in the cover picker.</summary>
public class ListFacesForPersonEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/people/{id:guid}/faces", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        [FromQuery] int? limit,
        [FromQuery] int? offset,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var person = await db.People.AsNoTracking()
            .FirstOrDefaultAsync(p => p.Id == id && p.OwnerId == userId, ct);
        if (person == null) return Results.NotFound();

        // Per-user identity lives in UserFaceAssignment. Pull faces this user
        // has confirmed for the given Person whose underlying asset is still
        // live. Visibility is implicit: if the user assigned this face to
        // their Person, they had read access to the asset at that point.
        var q = db.UserFaceAssignments.AsNoTracking()
            .Where(uf => uf.UserId == userId
                         && uf.PersonId == id
                         && !uf.IsRejected
                         && uf.Face.Asset.DeletedAt == null
                         && !uf.Face.Asset.IsFileMissing);

        var total = await q.CountAsync(ct);

        var items = await q
            .OrderByDescending(uf => uf.Face.Confidence)
            .Skip(offset ?? 0)
            .Take(Math.Clamp(limit ?? 60, 1, 200))
            .Select(uf => new PersonFaceItem(uf.FaceId, uf.Face.AssetId, uf.Face.Confidence, uf.IsManuallyAssigned))
            .ToListAsync(ct);

        return Results.Ok(new { total, items });
    }
}
