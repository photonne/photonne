using System.Globalization;
using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Returns the full content of one timeline bucket (a calendar month),
/// newest first, with tags hydrated — the same <see cref="TimelineResponse"/>
/// DTO the cursor timeline serves, so clients reuse their deserialization.
///
/// No internal pagination: a month is bounded in practice. If real libraries
/// ever show months beyond ~5k assets this is where an intra-bucket cursor
/// would go (known limit, see docs/timeline-buckets.md).
/// </summary>
public class TimelineBucketItemsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline/buckets/{yearMonth}", Handle)
            .WithName("GetTimelineBucketItems")
            .WithTags("Assets")
            .WithDescription("Returns all visible assets of one calendar month (timeline bucket), newest first")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromRoute] string yearMonth,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (!DateTime.TryParseExact(yearMonth, "yyyy-MM", CultureInfo.InvariantCulture,
                DateTimeStyles.None, out var parsed))
        {
            return Results.BadRequest(new { detail = "Expected a bucket key in 'yyyy-MM' format." });
        }

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

            // Half-open month range on CapturedAt. Equivalent to the
            // Year/Month GroupBy in TimelineBucketsEndpoint (both see the
            // stored UTC instant), but range-shaped so Postgres can walk the
            // CapturedAt index instead of computing date parts per row.
            var monthStart = DateTime.SpecifyKind(parsed, DateTimeKind.Utc);
            var monthEnd = monthStart.AddMonths(1);

            var items = await TimelineQuery.VisibleAssets(dbContext, allowedIds)
                .Where(a => a.CapturedAt >= monthStart && a.CapturedAt < monthEnd)
                .OrderByDescending(a => a.CapturedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .Select(TimelineProjection.ToResponse)
                .ToListAsync(cancellationToken);

            await TimelineQuery.HydrateTagsAsync(dbContext, items, cancellationToken);

            return Results.Ok(items);
        }
        catch (Exception ex)
        {
            return Results.Problem(detail: ex.Message, statusCode: StatusCodes.Status500InternalServerError);
        }
    }
}
