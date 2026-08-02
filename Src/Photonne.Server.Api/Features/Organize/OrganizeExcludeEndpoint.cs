using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Organize;

public record OrganizeExcludeRequest(List<Guid> AssetIds, bool Excluded);

/// <summary>
/// Marks assets as "never needs filing" — screenshots, memes, receipts — so the
/// "Para organizar" inbox stops counting them.
///
/// This is what lets the inbox reach zero. Without it there is a permanent
/// floor of things the user is never going to file, the badge never clears, and
/// a counter that can't reach zero stops carrying information.
///
/// Deliberately NOT archiving: archiving also pulls the asset out of the
/// timeline, and these are ordinary photos the user still wants to see. The
/// file doesn't move either — only its presence in the inbox changes.
///
/// Reversible by design (<c>excluded: false</c>), and the set-aside assets are
/// listable, so this is never a one-way door.
/// </summary>
public class OrganizeExcludeEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/organize/exclude", Handle)
            .WithName("SetOrganizeExcluded")
            .WithTags("Assets")
            .WithDescription("Marks assets as not needing to be organized (or undoes it)")
            .RequireAuthorization();

        app.MapGet("/api/organize/excluded", HandleList)
            .WithName("GetOrganizeExcluded")
            .WithTags("Assets")
            .WithDescription("Lists the caller's assets set aside from the organize inbox")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromBody] OrganizeExcludeRequest request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out _))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (request.AssetIds is not { Count: > 0 })
            return Results.Ok(new { updated = 0 });

        // Scoped to the caller's own MobileBackup prefix: the flag only means
        // anything there, and this keeps a stray id from touching someone
        // else's asset.
        var prefix = OrganizeQuery.MobileBackupPrefix(username);
        var ids = request.AssetIds.Distinct().ToList();

        var updated = await dbContext.Assets
            .Where(a => ids.Contains(a.Id)
                     && a.DeletedAt == null
                     && a.FullPath.StartsWith(prefix))
            .ExecuteUpdateAsync(
                s => s.SetProperty(a => a.ExcludedFromOrganize, request.Excluded),
                cancellationToken);

        return Results.Ok(new { updated });
    }

    private static async Task<IResult> HandleList(
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

        var query = OrganizeQuery.Excluded(dbContext, username);
        if (cursor.HasValue)
        {
            var cursorUtc = cursor.Value.ToUniversalTime();
            query = query.Where(a => a.CapturedAt < cursorUtc);
        }

        // Same shape as the inbox endpoint so the client reuses its grid.
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
