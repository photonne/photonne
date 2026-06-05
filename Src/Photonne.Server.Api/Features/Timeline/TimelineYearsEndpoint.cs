using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Returns the compressed yearly view: per year, the total visible-asset
/// count plus a sample distributed evenly across that year's timeline. Years
/// descend (newest first) and so do the items within each year, matching
/// every other timeline surface.
///
/// Sampling is two-phase: a lightweight (Id, CapturedAt) pass over the whole
/// visible library — the same cost class as the grid endpoint's full-library
/// projection — picks every ⌈count/sample⌉-th row per year in memory, then a
/// single ID-bounded query hydrates the picked rows through the shared
/// timeline projection.
/// </summary>
public class TimelineYearsEndpoint : IEndpoint
{
    private const int DefaultSample = 24;
    private const int MaxSample = 100;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline/years", Handle)
            .WithName("GetTimelineYears")
            .WithTags("Assets")
            .WithDescription("Returns per-year counts plus an evenly-distributed asset sample, newest first")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        [FromQuery] int? sample,
        CancellationToken cancellationToken)
    {
        var sampleSize = sample.GetValueOrDefault(DefaultSample);
        if (sampleSize <= 0) sampleSize = DefaultSample;
        if (sampleSize > MaxSample) sampleSize = MaxSample;

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

            // ── Phase 1: id + capture date of every visible asset, timeline
            // order (newest first). Two columns keep the row size tiny. ──────
            var all = await TimelineQuery.VisibleAssets(dbContext, allowedIds)
                .OrderByDescending(a => a.CapturedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .Select(a => new { a.Id, a.CapturedAt })
                .ToListAsync(cancellationToken);

            // GroupBy preserves the newest-first element order within each
            // group; years are emitted newest-first as well.
            var yearShapes = all
                .GroupBy(x => x.CapturedAt.Year)
                .OrderByDescending(g => g.Key)
                .Select(g =>
                {
                    var rows = g.ToList();
                    var step = (int)Math.Ceiling(rows.Count / (double)sampleSize);
                    var ids = rows
                        .Where((_, i) => i % step == 0)
                        .Take(sampleSize)
                        .Select(r => r.Id)
                        .ToList();
                    return (Year: g.Key, Count: rows.Count, Ids: ids);
                })
                .ToList();

            // ── Phase 2: hydrate the picked ids through the shared
            // projection and stitch them back in sampled order. ─────────────
            var sampledIds = yearShapes.SelectMany(y => y.Ids).ToList();
            var byId = new Dictionary<Guid, TimelineResponse>();
            if (sampledIds.Count > 0)
            {
                var items = await TimelineQuery.VisibleAssets(dbContext, allowedIds)
                    .Where(a => sampledIds.Contains(a.Id))
                    .Select(TimelineProjection.ToResponse)
                    .ToListAsync(cancellationToken);
                await TimelineQuery.HydrateTagsAsync(dbContext, items, cancellationToken);
                byId = items.ToDictionary(i => i.Id);
            }

            var response = yearShapes
                .Select(y => new TimelineYearResponse
                {
                    Year = y.Year,
                    Count = y.Count,
                    Items = y.Ids
                        .Where(byId.ContainsKey)
                        .Select(id => byId[id])
                        .ToList()
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
