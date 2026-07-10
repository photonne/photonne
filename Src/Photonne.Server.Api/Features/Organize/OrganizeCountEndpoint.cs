using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Organize;

/// <summary>
/// Cheap standalone count of the caller's "Para organizar" inbox, for the live
/// badge on the entry point. Uses the exact same predicate as the list endpoint
/// (<see cref="OrganizeQuery.Pending"/>) so the badge never disagrees with the
/// screen.
/// </summary>
public class OrganizeCountEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/organize/inbox/count", Handle)
            .WithName("GetOrganizeInboxCount")
            .WithTags("Assets")
            .WithDescription("Returns the number of unorganized (MobileBackup) assets for the current user")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out _))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var count = await OrganizeQuery.Pending(dbContext, username)
            .CountAsync(cancellationToken);

        return Results.Ok(new { count });
    }
}
