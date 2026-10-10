using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Canonical building blocks shared by every timeline-family endpoint
/// (timeline, recent, grid, index, neighbors and the upcoming bucket
/// endpoints — see docs/timeline-buckets.md).
///
/// <see cref="VisibleAssets"/> is THE single visibility predicate. Before
/// this helper each endpoint inlined its own copy and they had already
/// diverged: only /timeline and /recent excluded the motion (.mov) half of
/// Live Photos, so grid/index/neighbors surfaced clips the timeline never
/// renders. The bucket model turns count/content mismatches into visible
/// bugs (reserved skeleton heights), so the predicate must have exactly one
/// definition.
/// </summary>
internal static class TimelineQuery
{
    /// <summary>
    /// Assets visible on the requesting user's timeline: not deleted, not
    /// archived, file present on disk, inside an allowed folder, and not the
    /// motion (.mov) half of a Live Photo — the clip is served through the
    /// still's /motion endpoint, never as its own timeline item.
    /// </summary>
    public static IQueryable<Asset> VisibleAssets(
        ApplicationDbContext dbContext,
        HashSet<Guid> allowedFolderIds) =>
        dbContext.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                     && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value)
                     && !a.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart));

    /// <summary>
    /// Stitches detected-tag types and user-tag names onto a materialized
    /// page of <see cref="TimelineResponse"/> using two ID-bounded
    /// sub-queries. The alternative (Include + ThenInclude on the main query)
    /// multiplies the row count by the per-asset tag fan-out, so an 80-asset
    /// page can pull thousands of duplicated parent rows out of Postgres.
    /// No-ops on an empty page.
    /// </summary>
    public static async Task HydrateTagsAsync(
        ApplicationDbContext dbContext,
        List<TimelineResponse> items,
        CancellationToken ct)
    {
        if (items.Count == 0) return;

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
}
