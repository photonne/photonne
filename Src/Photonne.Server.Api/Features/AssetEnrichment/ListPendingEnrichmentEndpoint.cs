using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Organize;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetEnrichment;

/// <summary>
/// Paginated list of the caller's mobile-backup assets whose enrichment is
/// still open: a task queued, running or waiting out a retry, or one that
/// failed for good. Powers the "Backup enrichment status" screen and the two
/// rows of the backup card ("procesándose" / "con errores").
///
/// Only the latest row per (asset, type) counts: a failed attempt is never
/// reused — the next one is a fresh row — so the old Failed row stays behind
/// and, counted as-is, kept assets "processing" forever after they succeeded.
/// Scoped to the MobileBackup subtree so the backup screen doesn't report
/// work on library folders the phone never uploaded.
/// </summary>
public class ListPendingEnrichmentEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/enrichment/pending", Handle)
            .WithName("ListPendingEnrichment")
            .WithTags("Assets")
            .WithDescription("Lists the caller's mobile-backup assets with enrichment in flight or failed for good, ordered by FileCreatedAt desc.")
            .RequireAuthorization();
    }

    /// <param name="Pending">Queued, or Failed with a retry on the calendar:
    /// the server will get to it without anyone's help.</param>
    /// <param name="Failed">Out of attempts — only a manual retry revives it.</param>
    private sealed record PendingAssetDto(
        Guid AssetId,
        string FileName,
        DateTime FileCreatedAt,
        int Pending,
        int Processing,
        int Failed,
        IReadOnlyList<string> FailedTaskTypes);

    /// <param name="TotalAssets">Assets in the list: in flight or failed.</param>
    /// <param name="InFlightAssets">Assets with at least one task the server
    /// will still run on its own.</param>
    /// <param name="FailedAssets">Assets with at least one task out of
    /// attempts. Overlaps <paramref name="InFlightAssets"/> when an asset has
    /// both.</param>
    private sealed record PendingEnrichmentResponse(
        IReadOnlyList<PendingAssetDto> Items,
        DateTime? NextCursor,
        int TotalAssets,
        int InFlightAssets,
        int FailedAssets);

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
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var capped = Math.Clamp(pageSize <= 0 ? 50 : pageSize, 1, 200);
        var prefix = OrganizeQuery.MobileBackupPrefix(username);

        // Latest row per (asset, type) of the caller's live backup assets —
        // same "a newer attempt supersedes" rule as the failures registry.
        var current = dbContext.AssetEnrichmentTasks
            .Where(t => t.Asset.OwnerId == userId && t.Asset.DeletedAt == null
                     && t.Asset.FullPath.StartsWith(prefix))
            .Where(t => !dbContext.AssetEnrichmentTasks.Any(n =>
                n.AssetId == t.AssetId &&
                n.TaskType == t.TaskType &&
                n.CreatedAt > t.CreatedAt));

        var inFlight = current.Where(t =>
            t.Status == EnrichmentStatus.Pending ||
            t.Status == EnrichmentStatus.Processing ||
            (t.Status == EnrichmentStatus.Failed && t.NextRetryAt != null));
        var definitive = current.Where(t =>
            t.Status == EnrichmentStatus.Failed && t.NextRetryAt == null);
        var open = current.Where(t =>
            t.Status == EnrichmentStatus.Pending ||
            t.Status == EnrichmentStatus.Processing ||
            t.Status == EnrichmentStatus.Failed);

        var inFlightAssets = await inFlight.Select(t => t.AssetId).Distinct().CountAsync(cancellationToken);
        var failedAssets = await definitive.Select(t => t.AssetId).Distinct().CountAsync(cancellationToken);

        var baseQuery = dbContext.Assets
            .Where(a => open.Any(t => t.AssetId == a.Id));

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
                Array.Empty<PendingAssetDto>(), null, totalAssets, inFlightAssets, failedAssets));
        }

        // Load every task row for the assets on this page in one shot, then
        // aggregate in memory. Cheap because (pageSize × ~8 tasks) is bounded.
        var ids = pageAssets.Select(a => a.Id).ToList();
        var allTasks = await dbContext.AssetEnrichmentTasks
            .AsNoTracking()
            .Where(t => ids.Contains(t.AssetId))
            .Select(t => new { t.AssetId, t.TaskType, t.Status, t.NextRetryAt, t.CreatedAt })
            .ToListAsync(cancellationToken);

        var byAsset = allTasks.ToLookup(t => t.AssetId);

        var items = pageAssets.Select(a =>
        {
            // Same supersede rule as the counts, applied in memory.
            var tasks = byAsset[a.Id]
                .GroupBy(t => t.TaskType)
                .Select(g => g.MaxBy(t => t.CreatedAt)!)
                .ToList();
            var pending = tasks.Count(t => t.Status == EnrichmentStatus.Pending
                || (t.Status == EnrichmentStatus.Failed && t.NextRetryAt != null));
            var processing = tasks.Count(t => t.Status == EnrichmentStatus.Processing);
            var failedTypes = tasks
                .Where(t => t.Status == EnrichmentStatus.Failed && t.NextRetryAt == null)
                .Select(t => t.TaskType.ToString())
                .ToList();
            return new PendingAssetDto(
                a.Id, a.FileName, a.FileCreatedAt,
                pending, processing, failedTypes.Count, failedTypes);
        }).ToList();

        var nextCursor = hasMore ? pageAssets[^1].FileCreatedAt : (DateTime?)null;

        return Results.Ok(new PendingEnrichmentResponse(
            items, nextCursor, totalAssets, inFlightAssets, failedAssets));
    }
}
