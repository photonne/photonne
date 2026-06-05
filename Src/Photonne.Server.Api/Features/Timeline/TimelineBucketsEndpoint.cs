using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Returns the timeline skeleton: per-month visible-asset counts, newest
/// first. This is the entry point of the bucket model — the client renders
/// the whole scroll range from these counts and then loads each month's
/// content on demand via <see cref="TimelineBucketItemsEndpoint"/>.
///
/// Month granularity is fixed: the yearly view aggregates months client-side
/// and the daily view subdivides a loaded bucket client-side.
/// </summary>
public class TimelineBucketsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline/buckets", Handle)
            .WithName("GetTimelineBuckets")
            .WithTags("Assets")
            .WithDescription("Returns per-month visible-asset counts (the timeline skeleton), newest first")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        try
        {
            var userRootPath = $"/assets/users/{username}";
            var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
                dbContext, userId, userRootPath, cancellationToken);

            // Grouping key is CapturedAt's calendar month — the exact same
            // expression TimelineBucketItemsEndpoint filters by, so a bucket's
            // count always equals its content size.
            var buckets = await TimelineQuery.VisibleAssets(dbContext, allowedIds)
                .GroupBy(a => new { a.CapturedAt.Year, a.CapturedAt.Month })
                .Select(g => new { g.Key.Year, g.Key.Month, Count = g.Count() })
                .OrderByDescending(x => x.Year)
                .ThenByDescending(x => x.Month)
                .ToListAsync(cancellationToken);

            var response = buckets
                .Select(b => new TimelineBucketResponse
                {
                    Key = $"{b.Year:D4}-{b.Month:D2}",
                    Count = b.Count
                })
                .ToList();

            return Results.Ok(response);
        }
        catch (Exception ex)
        {
            return Results.Problem(detail: ex.Message, statusCode: StatusCodes.Status500InternalServerError);
        }
    }
}
