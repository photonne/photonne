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

public sealed record BulkSuggestionResult(int Affected);

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
                        && f.Asset.OwnerId == userId
                        && f.Asset.DeletedAt == null
                        && !f.Asset.IsFileMissing);

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

/// <summary>Confirms every pending face suggestion that points at the given person:
/// each face is attached to the person and marked manually-assigned (so clustering
/// won't move it). Returns the count of faces affected.</summary>
public class AcceptAllSuggestionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/{id:guid}/suggestions/accept-all", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] Photonne.Server.Api.Shared.Services.FaceRecognition.FaceClusteringService clustering,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var person = await db.People
            .FirstOrDefaultAsync(p => p.Id == id && p.OwnerId == userId, ct);
        if (person == null) return Results.NotFound();

        // Accept = manual assignment. ExecuteUpdate runs in one round trip and
        // sidesteps EF tracking — appropriate for a bulk operation that may
        // touch hundreds of faces.
        var affected = await db.Faces
            .Where(f => f.SuggestedPersonId == id
                        && f.PersonId == null
                        && !f.IsRejected
                        && f.Asset.OwnerId == userId
                        && f.Asset.DeletedAt == null
                        && !f.Asset.IsFileMissing)
            .ExecuteUpdateAsync(s => s
                .SetProperty(f => f.PersonId, (Guid?)id)
                .SetProperty(f => f.IsManuallyAssigned, true)
                .SetProperty(f => f.SuggestedPersonId, (Guid?)null)
                .SetProperty(f => f.SuggestedDistance, (float?)null), ct);

        if (affected > 0) await clustering.RecomputeFaceCountsAsync(userId, ct);
        return Results.Ok(new BulkSuggestionResult(affected));
    }
}

/// <summary>Clears every pending face suggestion that points at the given person.
/// The faces stay orphan; future clustering passes may surface the same
/// suggestions again — by design, dismiss is non-sticky.</summary>
public class DismissAllSuggestionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/{id:guid}/suggestions/dismiss-all", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var person = await db.People
            .FirstOrDefaultAsync(p => p.Id == id && p.OwnerId == userId, ct);
        if (person == null) return Results.NotFound();

        var affected = await db.Faces
            .Where(f => f.SuggestedPersonId == id
                        && f.PersonId == null
                        && !f.IsRejected
                        && f.Asset.OwnerId == userId)
            .ExecuteUpdateAsync(s => s
                .SetProperty(f => f.SuggestedPersonId, (Guid?)null)
                .SetProperty(f => f.SuggestedDistance, (float?)null), ct);

        return Results.Ok(new BulkSuggestionResult(affected));
    }
}
