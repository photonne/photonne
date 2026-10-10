using Photonne.Server.Api.Features.Memories.Generation;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Tests.Memories;

/// <summary>
/// Pure ranking maths — no DB. The score decides what the user sees first, so
/// the properties worth pinning are the monotonic ones: giving a memory MORE of
/// something good must never push it down the feed.
/// </summary>
public class MemoryScoringTests
{
    private static readonly DateTime Today = new(2026, 7, 14);

    private static MemoryDraft Draft(
        MemoryKind kind = MemoryKind.OnThisDay,
        int assetCount = 10,
        double favoriteRatio = 0,
        double namedFaceRatio = 0,
        int yearsAgo = 1)
    {
        var window = Today.AddYears(-yearsAgo);
        return new MemoryDraft
        {
            Kind = kind,
            DedupeKey = "k",
            ThemeKey = "k",
            GroupTitle = "g",
            Title = "t",
            AssetIds = Enumerable.Range(0, assetCount).Select(_ => Guid.NewGuid()).ToList(),
            CoverAssetId = Guid.NewGuid(),
            WindowStart = window,
            WindowEnd = window,
            FavoriteRatio = favoriteRatio,
            NamedFaceRatio = namedFaceRatio,
        };
    }

    [Fact]
    public void Score_StaysWithinUnitRange_AtBothExtremes()
    {
        var worst = MemoryScoring.Score(
            Draft(MemoryKind.PetsAndFood, assetCount: 1, yearsAgo: 50), Today);
        var best = MemoryScoring.Score(
            Draft(MemoryKind.PersonThroughYears, assetCount: 500, favoriteRatio: 1, namedFaceRatio: 1, yearsAgo: 0),
            Today);

        Assert.InRange(worst, 0.0, 1.0);
        Assert.InRange(best, 0.0, 1.0);
        Assert.True(best > worst);
    }

    [Fact]
    public void Score_RisesWithFavoriteRatio()
    {
        var few = MemoryScoring.Score(Draft(favoriteRatio: 0.1), Today);
        var many = MemoryScoring.Score(Draft(favoriteRatio: 0.9), Today);

        Assert.True(many > few);
    }

    [Fact]
    public void Score_RisesWithNamedFaceRatio()
    {
        var few = MemoryScoring.Score(Draft(namedFaceRatio: 0.0), Today);
        var many = MemoryScoring.Score(Draft(namedFaceRatio: 1.0), Today);

        Assert.True(many > few);
    }

    [Fact]
    public void Score_RisesWithAssetCount_ButSaturates()
    {
        var small = MemoryScoring.Score(Draft(assetCount: 3), Today);
        var medium = MemoryScoring.Score(Draft(assetCount: 30), Today);
        var huge = MemoryScoring.Score(Draft(assetCount: 5000), Today);

        Assert.True(medium > small);
        // Past ~100 assets the size term is pinned, so a 5000-photo memory must
        // not bury everything else in the feed on bulk alone.
        Assert.True(huge - medium < medium - small);
    }

    [Fact]
    public void Score_FavoursRecentWindows()
    {
        var recent = MemoryScoring.Score(Draft(yearsAgo: 1), Today);
        var ancient = MemoryScoring.Score(Draft(yearsAgo: 15), Today);

        Assert.True(recent > ancient);
    }

    [Fact]
    public void Score_DoesNotPunishBeyondTheRecencyHorizon()
    {
        // A 40-year-old memory is not "twice as stale" as a 20-year-old one;
        // past the horizon the term is flat, so age stops being a tie-breaker.
        var old = MemoryScoring.Score(Draft(yearsAgo: 25), Today);
        var older = MemoryScoring.Score(Draft(yearsAgo: 40), Today);

        Assert.Equal(old, older, precision: 10);
    }

    [Fact]
    public void Score_RanksPersonMemoriesOverSceneMemories_AllElseEqual()
    {
        var person = MemoryScoring.Score(Draft(MemoryKind.PersonThroughYears), Today);
        var scene = MemoryScoring.Score(Draft(MemoryKind.CuratedScene), Today);

        Assert.True(person > scene);
    }
}
