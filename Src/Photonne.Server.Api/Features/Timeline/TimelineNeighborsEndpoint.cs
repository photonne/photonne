using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Returns a lightweight window of timeline neighbors around a given asset,
/// used by AssetDetail for prev/next navigation without loading the full timeline.
/// </summary>
public class TimelineNeighborsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/timeline-neighbors", Handle)
            .WithName("GetTimelineNeighbors")
            .WithTags("Assets")
            .WithDescription("Returns a window of timeline neighbors around the given asset for navigation")
            .RequireAuthorization();
    }

    private async Task<IResult> Handle(
        [FromRoute] Guid assetId,
        [FromQuery] int before,
        [FromQuery] int after,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (before <= 0) before = 50;
        if (after <= 0) after = 50;
        if (before > 200) before = 200;
        if (after > 200) after = 200;

        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var target = await dbContext.Assets
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, ct);

        if (target == null)
            return Results.NotFound();

        // ── Permission check — shared with every timeline endpoint. Using the
        // cache also fixes a divergence: this inline copy never included
        // external-library folders, so library assets had no neighbors. ──
        var userRootPath = $"/assets/users/{username}";
        var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, ct);

        // Base query: the canonical timeline visibility predicate, so the
        // pager never navigates onto an asset the grid doesn't show.
        var baseQuery = TimelineQuery.VisibleAssets(dbContext, allowedIds);

        // ── Items BEFORE in timeline (newer CapturedAt = appear earlier in DESC order) ──
        // Includes same-date items with newer FileModifiedAt for correct tie-breaking.
        var beforeItems = await baseQuery
            .Where(a =>
                a.CapturedAt > target.CapturedAt
                || (a.CapturedAt == target.CapturedAt
                    && a.FileModifiedAt > target.FileModifiedAt))
            .OrderBy(a => a.CapturedAt)
            .ThenBy(a => a.FileModifiedAt)
            .Select(a => new TimelineNeighborItem
            {
                Id = a.Id,
                FullPath = a.FullPath,
                FileCreatedAt = a.CapturedAt
            })
            .Take(before + 1)
            .ToListAsync(ct);

        var hasMoreBefore = beforeItems.Count > before;
        if (hasMoreBefore) beforeItems = beforeItems.Take(before).ToList();
        beforeItems.Reverse(); // Now in timeline (DESC) order

        // ── Items AFTER in timeline (older CapturedAt or same-date with older FileModifiedAt) ──
        var afterItems = await baseQuery
            .Where(a => a.Id != assetId && (
                a.CapturedAt < target.CapturedAt
                || (a.CapturedAt == target.CapturedAt
                    && a.FileModifiedAt <= target.FileModifiedAt)))
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .Select(a => new TimelineNeighborItem
            {
                Id = a.Id,
                FullPath = a.FullPath,
                FileCreatedAt = a.CapturedAt
            })
            .Take(after + 1)
            .ToListAsync(ct);

        var hasMoreAfter = afterItems.Count > after;
        if (hasMoreAfter) afterItems = afterItems.Take(after).ToList();

        // ── Combine: before + target + after ──
        var targetItem = new TimelineNeighborItem
        {
            Id = target.Id,
            FullPath = target.FullPath,
            FileCreatedAt = target.CapturedAt
        };

        var items = new List<TimelineNeighborItem>(beforeItems.Count + 1 + afterItems.Count);
        items.AddRange(beforeItems);
        items.Add(targetItem);
        items.AddRange(afterItems);

        return Results.Ok(new TimelineNeighborsResponse
        {
            Items = items,
            CurrentIndex = beforeItems.Count,
            HasMoreBefore = hasMoreBefore,
            HasMoreAfter = hasMoreAfter
        });
    }
}
