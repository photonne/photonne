using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Returns the N most recent timeline assets the requesting user can read.
/// Lives alongside <see cref="TimelineEndpoint"/> but is intentionally far
/// cheaper: same projection (so the client deserializes into the same DTO),
/// but no filesystem scan, no path resolution, no pagination plumbing and
/// no tag join — the hub's recents row just needs id + thumbnail + type.
///
/// The hub used to call <c>/api/assets/timeline?pageSize=80</c> and slice the
/// first 10 items client-side, which dragged in the entire expensive code
/// path; with this endpoint the hub stays fast even when the heavy timeline
/// query is still slow (e.g. cold cache, large library).
/// </summary>
public class RecentAssetsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/recent", Handle)
            .WithName("GetRecentAssets")
            .WithTags("Assets")
            .WithDescription("Returns the N most recent visible assets (slim hub-tile projection)")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        [FromQuery] int? limit,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
        {
            return Results.Unauthorized();
        }
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var take = limit.GetValueOrDefault(10);
        if (take <= 0) take = 10;
        if (take > 100) take = 100;

        var userRootPath = $"/assets/users/{username}";
        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);

        var items = await TimelineQuery.VisibleAssets(dbContext, allowedFolderIds)
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .Take(take)
            .Select(TimelineProjection.ToResponse)
            .ToListAsync(cancellationToken);

        return Results.Ok(items);
    }
}
