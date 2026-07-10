using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Organize;

public class OrganizeInboxPageResponse
{
    public List<TimelineResponse> Items { get; set; } = new();
    public bool HasMore { get; set; }
    // CapturedAt of the oldest item returned; pass as `cursor` for the next page.
    public DateTime? NextCursor { get; set; }
}

/// <summary>
/// Lists the "Para organizar" inbox: assets still under the caller's
/// MobileBackup subtree (dropped by automatic backup, not yet filed into a
/// folder), newest first, paginated by CapturedAt with an exclusive cursor.
/// Reuses the timeline projection so the client deserializes the same
/// <see cref="TimelineResponse"/> DTO it already uses for the grid.
/// </summary>
public class OrganizeInboxEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/organize/inbox", Handle)
            .WithName("GetOrganizeInbox")
            .WithTags("Assets")
            .WithDescription("Lists the current user's unorganized (MobileBackup) assets, newest first")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int pageSize,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out _))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (pageSize <= 0) pageSize = 150;
        if (pageSize > 500) pageSize = 500;

        var query = OrganizeQuery.Pending(dbContext, username);

        if (cursor.HasValue)
        {
            var cursorUtc = cursor.Value.ToUniversalTime();
            query = query.Where(a => a.CapturedAt < cursorUtc);
        }

        var page = await query
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .Take(pageSize + 1)
            .Select(TimelineProjection.ToResponse)
            .ToListAsync(cancellationToken);

        var hasMore = page.Count > pageSize;
        var items = hasMore ? page.Take(pageSize).ToList() : page;

        await TimelineQuery.HydrateTagsAsync(dbContext, items, cancellationToken);

        // FileCreatedAt carries the CapturedAt value (see TimelineProjection).
        var nextCursor = hasMore ? items.Last().FileCreatedAt : (DateTime?)null;

        return Results.Ok(new OrganizeInboxPageResponse
        {
            Items = items,
            HasMore = hasMore,
            NextCursor = nextCursor
        });
    }
}
