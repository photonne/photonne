using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories;

/// <summary>
/// The Recuerdos feed, read straight off the precomputed table.
///
/// No visibility gate runs here beyond OwnerId equality, and that is by design:
/// a Memory row was generated inside its owner's
/// <see cref="Shared.Authorization.AssetVisibilityService"/> scope, so the rows
/// are already the answer. The cost is that a permission revoked after the
/// nightly run leaks until the next one — acceptable for a feed of the user's
/// own photos, and the asset endpoints still gate every actual byte.
/// </summary>
public class MemoryFeedEndpoint : IEndpoint
{
    private const int DefaultLimit = 50;

    /// <summary>
    /// High because the native client groups the feed into rows by theme and
    /// needs the whole set to do it. Truncating by Score cuts across those rows
    /// rather than along them: at 200, a heavy library silently loses 2019 from
    /// the middle of "Días de playa" with nothing to show it happened. A memory
    /// is ~10 scalars off an index-covered query, so 500 rows is around 100 KB
    /// and no joins. If this ever stops being enough the answer is a grouped
    /// endpoint that truncates per row, not a bigger number.
    /// </summary>
    private const int MaxLimit = 500;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/memories", Handle)
            .WithName("GetMemoryFeed")
            .WithTags("Memories")
            .WithDescription("Returns the user's generated memories, best first")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        [FromQuery] string? kind,
        [FromQuery] int? limit,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        var take = Math.Clamp(limit.GetValueOrDefault(DefaultLimit), 1, MaxLimit);

        var query = db.Memories
            .AsNoTracking()
            .Where(m => m.OwnerId == userId && !m.IsDismissed);

        if (!string.IsNullOrWhiteSpace(kind))
        {
            if (!Enum.TryParse<MemoryKind>(kind, ignoreCase: true, out var parsed))
                return Results.BadRequest(new { message = $"Unknown memory kind '{kind}'." });
            query = query.Where(m => m.Kind == parsed);
        }

        var items = await query
            .OrderByDescending(m => m.Score)
            .ThenByDescending(m => m.WindowStart)
            .Take(take)
            .Select(m => new MemoryResponse
            {
                Id = m.Id,
                Kind = m.Kind.ToString(),
                Title = m.Title,
                Subtitle = m.Subtitle,
                ThemeKey = m.ThemeKey,
                GroupTitle = m.GroupTitle,
                CardLabel = m.CardLabel,
                CoverAssetId = m.CoverAssetId,
                AssetCount = m.AssetCount,
                WindowStart = m.WindowStart,
                WindowEnd = m.WindowEnd,
            })
            .ToListAsync(ct);

        return Results.Ok(items);
    }
}

/// <summary>A single memory with its assets, for opening the viewer.</summary>
public class MemoryDetailEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/memories/{id:guid}", Handle)
            .WithName("GetMemory")
            .WithTags("Memories")
            .WithDescription("Returns a memory and the assets it holds, in display order")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] AssetVisibilityService visibility,
        ClaimsPrincipal user,
        Guid id,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        var memory = await db.Memories
            .AsNoTracking()
            .Where(m => m.Id == id && m.OwnerId == userId)
            .Select(m => new MemoryDetailResponse
            {
                Id = m.Id,
                Kind = m.Kind.ToString(),
                Title = m.Title,
                Subtitle = m.Subtitle,
                ThemeKey = m.ThemeKey,
                GroupTitle = m.GroupTitle,
                CardLabel = m.CardLabel,
                CoverAssetId = m.CoverAssetId,
                AssetCount = m.AssetCount,
                WindowStart = m.WindowStart,
                WindowEnd = m.WindowEnd,
            })
            .FirstOrDefaultAsync(ct);

        // 404 for both "no such memory" and "not yours" — a distinct 403 would
        // confirm the id exists to someone who shouldn't know that.
        if (memory is null) return Results.NotFound();

        // Ordered by Position, and re-gated: a memory generated last night may
        // point at an asset since deleted, archived, unshared, or sitting in a
        // folder the user has excluded from their surfaces since. The row is a
        // nightly snapshot; the gate has to run now, or excluding a folder would
        // do nothing until the next pass — and a revoked permission would keep
        // handing photos back until then too.
        var scope = await visibility.GetScopeAsync(userId, ct);
        memory.Assets = await db.MemoryAssets
            .AsNoTracking()
            .Where(ma => ma.MemoryId == id)
            .OrderBy(ma => ma.Position)
            .Select(ma => ma.Asset)
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing)
            .Where(scope.DiscoveryPredicate())
            .Select(TimelineProjection.ToResponse)
            .ToListAsync(ct);

        await TimelineQuery.HydrateTagsAsync(db, memory.Assets, ct);
        return Results.Ok(memory);
    }
}
