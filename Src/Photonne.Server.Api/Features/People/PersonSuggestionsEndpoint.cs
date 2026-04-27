using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.People;

public sealed record PersonSuggestionItem(
    Guid Id,
    Guid AssetId,
    float Confidence,
    float? SuggestedDistance);

/// <summary>Lists orphan faces that the clustering service flagged as "could be
/// this person" (cosine distance in the soft-match band). Ordered by suggested
/// distance ascending so the strongest candidates surface first.</summary>
public class ListPersonSuggestionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/people/{id:guid}/suggestions", Handle)
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

        var q = db.Faces.AsNoTracking()
            .Where(f => f.SuggestedPersonId == id
                        && f.PersonId == null
                        && !f.IsRejected
                        && f.Asset.OwnerId == userId);

        var total = await q.CountAsync(ct);

        var items = await q
            .OrderBy(f => f.SuggestedDistance)
            .ThenByDescending(f => f.Confidence)
            .Skip(offset ?? 0)
            .Take(Math.Clamp(limit ?? 30, 1, 100))
            .Select(f => new PersonSuggestionItem(f.Id, f.AssetId, f.Confidence, f.SuggestedDistance))
            .ToListAsync(ct);

        return Results.Ok(new { total, items });
    }
}
