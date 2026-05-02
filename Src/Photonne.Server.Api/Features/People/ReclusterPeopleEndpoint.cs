using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Features.People;

public record ReclusterResponse(int PersonsCreated);

/// <summary>Forces an "ensure up to date" pass over faces visible to the user:
/// online-attaches any face the user does not yet have an assignment for to
/// their existing Persons, then runs a batch cluster pass over the remaining
/// orphans. Used right after enabling face recognition, after a backfill, or
/// when a user has just gained read access to a shared album/folder/external
/// library and wants to see the faces detected on those assets.</summary>
public class ReclusterPeopleEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/recluster", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] FaceClusteringService clustering,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        // Force a full pass: online attach + batch (ignoring the per-user
        // cooldown). The user explicitly asked us to do work, so the cooldown
        // — meant for the implicit per-detection trigger — shouldn't gate it.
        var created = await clustering.ForceRunForUserAsync(userId, ct);
        return Results.Ok(new ReclusterResponse(created));
    }
}
