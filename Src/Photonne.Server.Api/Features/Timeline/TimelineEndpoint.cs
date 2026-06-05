using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Scalar.AspNetCore;

namespace Photonne.Server.Api.Features.Timeline;

public class TimelineEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline", Handle)
        .CodeSample(
                codeSample: "curl -X GET \"http://localhost:5000/api/assets/timeline\" -H \"Accept: application/json\"",
                label: "cURL Example")
        .WithName("GetTimeline")
        .WithTags("Assets")
        .WithDescription("Gets the timeline of all scanned media files (images and videos)")
        .AddOpenApiOperationTransformer((operation, context, ct) =>
        {
            operation.Summary = "Gets the timeline";
            operation.Description = "Returns all media assets stored in the database, ordered by the most recently scanned first, then by modification date";
            return Task.CompletedTask;
        });
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] DateTime? from,
        [FromQuery] int pageSize,
        CancellationToken cancellationToken)
    {
        if (pageSize <= 0) pageSize = 150;
        if (pageSize > 500) pageSize = 500;

        try
        {
            if (!TryGetUserId(user, out var userId))
            {
                return Results.Unauthorized();
            }
            var username = user.GetUsername();
            if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

            var userRootPath = $"/assets/users/{username}";
            var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
                dbContext, userId, userRootPath, cancellationToken);

            var query = TimelineQuery.VisibleAssets(dbContext, allowedFolderIds);

            // Apply cursor (exclusive upper bound on CapturedAt — the timeline
            // sort key, not the filesystem mtime).
            if (cursor.HasValue)
            {
                var cursorUtc = cursor.Value.ToUniversalTime();
                query = query.Where(a => a.CapturedAt < cursorUtc);
            }

            if (from.HasValue)
            {
                var fromUtc = from.Value.ToUniversalTime();
                query = query.Where(a => a.CapturedAt >= fromUtc);
            }

            // Fetch one extra item to determine hasMore. The projection itself
            // is shared with /api/assets/recent — see TimelineProjection.
            var page = await query
                .OrderByDescending(a => a.CapturedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .Take(pageSize + 1)
                .Select(TimelineProjection.ToResponse)
                .ToListAsync(cancellationToken);

            var hasMore = page.Count > pageSize;
            var items = hasMore ? page.Take(pageSize).ToList() : page;

            // Tags travel separately from the main projection so we don't pay
            // the asset×thumbnail×tag×userTag cartesian fan-out up front. Two
            // small "WHERE AssetId IN (…)" queries are cheap and let EF stay
            // on the indexed projection above.
            await TimelineQuery.HydrateTagsAsync(dbContext, items, cancellationToken);

            var nextCursor = hasMore ? items.Last().FileCreatedAt : (DateTime?)null;

            return Results.Ok(new TimelinePageResponse
            {
                Items = items,
                HasMore = hasMore,
                NextCursor = nextCursor
            });
        }
        catch (Exception ex)
        {
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(userIdClaim?.Value, out userId);
    }
}
