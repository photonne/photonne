using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.AssetEnrichment;

/// <summary>
/// Paginated list of the caller's assets that still have at least one
/// <see cref="EnrichmentStatus.Pending"/> or <see cref="EnrichmentStatus.Failed"/>
/// task. Powers the "Backup enrichment status" screen.
/// </summary>
public class ListPendingEnrichmentEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/enrichment/pending", Handle)
            .WithName("ListPendingEnrichment")
            .WithTags("Assets")
            .WithDescription("Lists the caller's assets with at least one Pending or Failed enrichment task, ordered by FileCreatedAt desc.")
            .RequireAuthorization();
    }

    private sealed record PendingAssetDto(
        Guid AssetId,
        string FileName,
        DateTime FileCreatedAt,
        int Pending,
        int Processing,
        int Failed,
        IReadOnlyList<string> FailedTaskTypes);

    private sealed record PendingEnrichmentResponse(
        IReadOnlyList<PendingAssetDto> Items,
        DateTime? NextCursor,
        int TotalAssets);

    private async Task<IResult> Handle(
        [FromQuery] int pageSize,
        [FromQuery] DateTime? cursor,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        var capped = Math.Clamp(pageSize <= 0 ? 50 : pageSize, 1, 200);

        // Base predicate: asset belongs to user, not deleted, has at least one
        // task that is Pending or Failed.
        var baseQuery = dbContext.Assets
            .Where(a => a.OwnerId == userId && a.DeletedAt == null)
            .Where(a => dbContext.AssetEnrichmentTasks.Any(t =>
                t.AssetId == a.Id &&
                (t.Status == EnrichmentStatus.Pending || t.Status == EnrichmentStatus.Failed)));

        var totalAssets = await baseQuery.CountAsync(cancellationToken);

        // Page by FileCreatedAt descending: newest backups surface first.
        var pageQuery = baseQuery;
        if (cursor.HasValue)
            pageQuery = pageQuery.Where(a => a.FileCreatedAt < cursor.Value);

        var pageAssets = await pageQuery
            .OrderByDescending(a => a.FileCreatedAt)
            .Take(capped + 1)
            .Select(a => new { a.Id, a.FileName, a.FileCreatedAt })
            .ToListAsync(cancellationToken);

        var hasMore = pageAssets.Count > capped;
        if (hasMore) pageAssets.RemoveAt(pageAssets.Count - 1);

        if (pageAssets.Count == 0)
        {
            return Results.Ok(new PendingEnrichmentResponse(
                Array.Empty<PendingAssetDto>(), null, totalAssets));
        }

        // Load every task row for the assets on this page in one shot, then
        // aggregate in memory. Cheap because (pageSize × ~8 tasks) is bounded.
        var ids = pageAssets.Select(a => a.Id).ToList();
        var allTasks = await dbContext.AssetEnrichmentTasks
            .AsNoTracking()
            .Where(t => ids.Contains(t.AssetId))
            .Select(t => new { t.AssetId, t.TaskType, t.Status })
            .ToListAsync(cancellationToken);

        var byAsset = allTasks.ToLookup(t => t.AssetId);

        var items = pageAssets.Select(a =>
        {
            var tasks = byAsset[a.Id].ToList();
            var pending    = tasks.Count(t => t.Status == EnrichmentStatus.Pending);
            var processing = tasks.Count(t => t.Status == EnrichmentStatus.Processing);
            var failed     = tasks.Count(t => t.Status == EnrichmentStatus.Failed);
            var failedTypes = tasks
                .Where(t => t.Status == EnrichmentStatus.Failed)
                .Select(t => t.TaskType.ToString())
                .ToList();
            return new PendingAssetDto(
                a.Id, a.FileName, a.FileCreatedAt,
                pending, processing, failed, failedTypes);
        }).ToList();

        var nextCursor = hasMore ? pageAssets[^1].FileCreatedAt : (DateTime?)null;

        return Results.Ok(new PendingEnrichmentResponse(items, nextCursor, totalAssets));
    }
}
