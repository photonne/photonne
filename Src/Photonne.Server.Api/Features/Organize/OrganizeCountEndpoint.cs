using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Organize;

/// <summary>
/// Cheap standalone summary of the caller's "Para organizar" inbox, for the live
/// badge on the entry point and the backlog header. Uses the exact same predicate
/// as the list endpoint (<see cref="OrganizeQuery.Pending"/>) so the badge never
/// disagrees with the screen.
///
/// Also returns the capture-date span, which a count alone can't convey: "1.240
/// sin organizar" reads the same whether it's last week's trip or four years of
/// backlog, and those call for completely different decisions. Getting the span
/// from the client would mean paging the whole inbox to reach the oldest item;
/// here it is one MIN/MAX over the same indexed predicate.
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

        var pending = OrganizeQuery.Pending(dbContext, username);

        // Single round trip: counting and then re-querying for the span would
        // hit this on every badge refresh.
        var summary = await pending
            .GroupBy(_ => 1)
            .Select(g => new
            {
                count = g.Count(),
                oldest = (DateTime?)g.Min(a => a.CapturedAt),
                newest = (DateTime?)g.Max(a => a.CapturedAt),
            })
            .FirstOrDefaultAsync(cancellationToken);

        // An empty inbox groups to nothing, which is the good case — report a
        // zero count rather than letting the client read a missing body.
        return Results.Ok(summary ?? new { count = 0, oldest = (DateTime?)null, newest = (DateTime?)null });
    }
}
