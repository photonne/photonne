using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
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

            var query = dbContext.Assets
                .AsNoTracking()
                .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                         && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value)
                         // Hide the motion (.mov) half of a Live Photo: it's served
                         // through the still's /motion endpoint, not as its own item.
                         && !a.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart));

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
            // on the indexed projection above. Skip when the page is empty.
            if (items.Count > 0)
            {
                await HydrateTagsAsync(dbContext, items, cancellationToken);
            }

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

    /// <summary>
    /// Stitches detected-tag types and user-tag names onto a materialized page
    /// of <see cref="TimelineResponse"/> using two ID-bounded sub-queries. The
    /// alternative (Include + ThenInclude on the main query) multiplies the
    /// row count by the per-asset tag fan-out, so a 80-asset page can pull
    /// thousands of duplicated parent rows out of Postgres.
    /// </summary>
    private static async Task HydrateTagsAsync(
        ApplicationDbContext dbContext,
        List<TimelineResponse> items,
        CancellationToken ct)
    {
        var assetIds = items.Select(i => i.Id).ToList();

        var autoTagRows = await dbContext.AssetTags
            .AsNoTracking()
            .Where(t => assetIds.Contains(t.AssetId))
            .Select(t => new { t.AssetId, Label = t.TagType.ToString() })
            .ToListAsync(ct);

        var userTagRows = await dbContext.AssetUserTags
            .AsNoTracking()
            .Where(t => assetIds.Contains(t.AssetId))
            .Select(t => new { t.AssetId, Label = t.UserTag.Name })
            .ToListAsync(ct);

        var byAsset = autoTagRows.Concat(userTagRows)
            .GroupBy(r => r.AssetId)
            .ToDictionary(
                g => g.Key,
                g => g.Select(r => r.Label)
                      .Distinct(StringComparer.OrdinalIgnoreCase)
                      .OrderBy(t => t, StringComparer.OrdinalIgnoreCase)
                      .ToList());

        foreach (var item in items)
        {
            if (byAsset.TryGetValue(item.Id, out var labels))
            {
                item.Tags = labels;
            }
        }
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(userIdClaim?.Value, out userId);
    }
}
