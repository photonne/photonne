using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Ranks memories against each other for the feed. All weights live here, in one
/// table, so tuning is one edit rather than a hunt across generators.
/// </summary>
internal static class MemoryScoring
{
    // How much each kind is worth before any per-memory evidence. Person-driven
    // memories lead because a named face is the strongest signal we have that a
    // photo matters to someone; scene/object memories trail because they rest on
    // a classifier's opinion, not the user's.
    private static readonly Dictionary<MemoryKind, double> KindWeight = new()
    {
        [MemoryKind.PersonThroughYears] = 1.00,
        [MemoryKind.PeopleTogether]     = 0.95,
        [MemoryKind.Trip]               = 0.90,
        [MemoryKind.OnThisDay]          = 0.85,
        [MemoryKind.FavoritesOfYear]    = 0.75,
        [MemoryKind.ThisMonth]          = 0.55,
        [MemoryKind.CuratedScene]       = 0.45,
        [MemoryKind.PetsAndFood]        = 0.40,
    };

    private const double KindShare      = 0.30;
    private const double FavoriteShare  = 0.25;
    private const double NamedFaceShare = 0.20;
    private const double SizeShare      = 0.15;
    private const double RecencyShare   = 0.10;

    /// <summary>Beyond this many years back, a memory stops losing points — a
    /// 30-year-old photo isn't twice as stale as a 15-year-old one.</summary>
    private const double RecencyHorizonYears = 20.0;

    /// <summary>
    /// Feed rank in 0..1. Monotonic in every input: more favourites, more named
    /// faces, more assets or a more recent window never lowers the score.
    /// </summary>
    public static double Score(MemoryDraft draft, DateTime localToday)
    {
        var kind = KindWeight.TryGetValue(draft.Kind, out var w) ? w : 0.5;

        // log10 so the 5th photo counts for a lot and the 500th for almost
        // nothing — saturates at 100 assets.
        var size = Math.Min(1.0, Math.Log10(Math.Max(1, draft.AssetIds.Count)) / 2.0);

        var yearsAgo = (localToday - draft.WindowEnd).TotalDays / 365.25;
        var recency = 1.0 - Math.Clamp(yearsAgo / RecencyHorizonYears, 0.0, 1.0);

        return KindShare      * kind
             + FavoriteShare  * Math.Clamp(draft.FavoriteRatio, 0.0, 1.0)
             + NamedFaceShare * Math.Clamp(draft.NamedFaceRatio, 0.0, 1.0)
             + SizeShare      * size
             + RecencyShare   * recency;
    }
}
