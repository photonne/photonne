using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Features.People;

public record ReclusterResponse(int PersonsCreated);

/// <summary>Forces a batch clustering pass over the current user's orphan faces.
/// Useful right after enabling face recognition for the first time, or after a
/// backfill, when no Persons exist yet so online assignment can't kick in.</summary>
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

        var created = await clustering.RunForOwnerAsync(userId, ct);
        return Results.Ok(new ReclusterResponse(created));
    }
}
