using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>The per-asset facts every generator needs: enough to order a set,
/// pick a cover and compute the score ratios, and nothing more. Deliberately a
/// flat projection — pulling whole <see cref="Asset"/> graphs for a memory that
/// may hold hundreds of photos is how the old endpoint got its cartesian join.</summary>
internal sealed record MemoryCandidate(
    Guid Id,
    DateTime CapturedAt,
    bool IsFavorite,
    bool HasNamedFace,
    bool IsLandscape);

internal static class MemoryCandidates
{
    /// <summary>Hard ceiling on the assets any single memory holds. Nobody scrolls
    /// 400 photos, and it bounds the MemoryAsset fan-out per nightly run.</summary>
    public const int MaxAssetsPerMemory = 200;

    /// <summary>
    /// Materializes a candidate set, newest first. <paramref name="userId"/> is the
    /// identity frame for "named face": faces are shared, but the naming is private
    /// to each user (<see cref="UserFaceAssignment"/>), so the same photo can be
    /// face-worthy for one viewer and anonymous for another.
    /// </summary>
    public static Task<List<MemoryCandidate>> LoadAsync(
        IQueryable<Asset> query,
        Guid userId,
        ApplicationDbContext db,
        CancellationToken ct,
        int take = MaxAssetsPerMemory) =>
        query
            .OrderByDescending(a => a.CapturedAt)
            .Take(take)
            .Select(a => new MemoryCandidate(
                a.Id,
                a.CapturedAt,
                a.IsFavorite,
                a.Faces.Any(f => db.UserFaceAssignments.Any(uf =>
                    uf.FaceId == f.Id
                    && uf.UserId == userId
                    && uf.PersonId != null
                    && !uf.IsRejected
                    && uf.Person!.Name != null)),
                a.Exif != null && a.Exif.Width != null && a.Exif.Height != null
                    && a.Exif.Width > a.Exif.Height))
            .ToListAsync(ct);

    /// <summary>
    /// The cover, by descending priority: a favourite of someone you named, then
    /// any favourite, then anyone you named, then a landscape crop (memory cards
    /// are wider than tall), then simply the newest. Every tier is a signal the
    /// user gave us, ordered by how deliberate it was.
    /// </summary>
    public static MemoryCandidate PickCover(IEnumerable<MemoryCandidate> candidates) =>
        candidates
            .OrderByDescending(c => c.IsFavorite && c.HasNamedFace)
            .ThenByDescending(c => c.IsFavorite)
            .ThenByDescending(c => c.HasNamedFace)
            .ThenByDescending(c => c.IsLandscape)
            .ThenByDescending(c => c.CapturedAt)
            .First();

    /// <summary>Builds the draft shared by every generator: cover, window and the
    /// two score ratios, all derived from the same candidate set.</summary>
    public static MemoryDraft ToDraft(
        this IReadOnlyList<MemoryCandidate> candidates,
        MemoryKind kind,
        string dedupeKey,
        string themeKey,
        string groupTitle,
        string title,
        string? subtitle = null,
        string? cardLabel = null)
    {
        var cover = PickCover(candidates);
        // Cover first: the client opens the viewer at index 0 and morphs from the
        // cover thumbnail, so the two must agree.
        var ordered = new List<Guid> { cover.Id };
        ordered.AddRange(candidates.Where(c => c.Id != cover.Id)
            .OrderByDescending(c => c.CapturedAt)
            .Select(c => c.Id));

        return new MemoryDraft
        {
            Kind = kind,
            DedupeKey = dedupeKey,
            ThemeKey = themeKey,
            GroupTitle = groupTitle,
            Title = title,
            Subtitle = subtitle,
            CardLabel = cardLabel,
            AssetIds = ordered,
            CoverAssetId = cover.Id,
            WindowStart = candidates.Min(c => c.CapturedAt),
            WindowEnd = candidates.Max(c => c.CapturedAt),
            FavoriteRatio = candidates.Count(c => c.IsFavorite) / (double)candidates.Count,
            NamedFaceRatio = candidates.Count(c => c.HasNamedFace) / (double)candidates.Count,
        };
    }
}
