using Photonne.Server.Api.Features.VideoSummaries.Highlights;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;
using static Photonne.Server.Api.Tests.VideoSummaries.HighlightFinderTests;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// The planner with analysed videos: time goes by merit (and favourites),
/// the dullest videos are the ones left out, each video's time is spent on its
/// best moments, and the summary still never runs past its length.
/// </summary>
public sealed class SummaryPlannerHighlightsTests
{
    private static readonly DateTime Jan1 = new(2024, 1, 1, 12, 0, 0);

    private static VideoProfile Lively(int seconds, int from, int to) =>
        Profile(seconds, s => s >= from && s < to ? (8, 120, 3, -18) : (2, 120, 4, -35));

    private static VideoProfile Dull(int seconds) => Profile(seconds, _ => (1, 120, 6, -40));

    [Fact]
    public void AGoodVideo_GetsMoreTime_ThanADullOne_AndItsBestMoment()
    {
        var good = new SummaryCandidate(Guid.NewGuid(), true, Jan1, 120);
        var dull = new SummaryCandidate(Guid.NewGuid(), true, Jan1.AddDays(1), 120);
        var profiles = new Dictionary<Guid, VideoProfile>
        {
            [good.AssetId] = Lively(120, 60, 72),
            [dull.AssetId] = Dull(120),
        };

        var plan = SummaryPlanner.Plan(new[] { good, dull }, false,
            new SummaryPlanOptions { TargetSeconds = 30 }, profiles);

        var goodTime = plan.Where(c => c.AssetId == good.AssetId).Sum(c => c.Length);
        var dullTime = plan.Where(c => c.AssetId == dull.AssetId).Sum(c => c.Length);
        Assert.True(goodTime > dullTime * 2, $"good {goodTime}, dull {dullTime}");
        Assert.Contains(plan, c => c.AssetId == good.AssetId && c.Start < 72 && c.Start + c.Length > 60);
        Assert.True(plan.Sum(c => c.Length) <= 30 + 1e-6);
    }

    [Fact]
    public void WhenNotAllFit_TheDullestAreLeftOut_FavouritesLast()
    {
        // 40 videos for a one-minute summary: some must go.
        var videos = Enumerable.Range(0, 40)
            .Select(i => new SummaryCandidate(Guid.NewGuid(), true, Jan1.AddDays(i * 9), 60, IsFavorite: i == 7))
            .ToList();
        var profiles = videos.ToDictionary(
            v => v.AssetId,
            v => v.IsFavorite || videos.IndexOf(v) % 4 == 0 ? Lively(60, 20, 30) : Dull(60));

        var plan = SummaryPlanner.Plan(videos, false, new SummaryPlanOptions { TargetSeconds = 60 }, profiles);
        var used = plan.Select(c => c.AssetId).ToHashSet();

        Assert.Contains(videos[7].AssetId, used);
        Assert.True(used.Count(id => profiles[id].Motion[25] > 5) >= used.Count / 2,
            "most of what's left is the lively videos");
        Assert.True(plan.Sum(c => c.Length) <= 60 + 1e-6);
        Assert.All(plan, c => Assert.True(c.Length >= 3 - 1e-6));
    }

    [Fact]
    public void AFavourite_GetsMoreTime_ThanAnEqualVideo()
    {
        var plain = new SummaryCandidate(Guid.NewGuid(), true, Jan1, 90);
        var favourite = new SummaryCandidate(Guid.NewGuid(), true, Jan1.AddDays(1), 90, IsFavorite: true);
        var profiles = new Dictionary<Guid, VideoProfile>
        {
            [plain.AssetId] = Lively(90, 30, 50),
            [favourite.AssetId] = Lively(90, 30, 50),
        };

        var plan = SummaryPlanner.Plan(new[] { plain, favourite }, false,
            new SummaryPlanOptions { TargetSeconds = 30 }, profiles);

        Assert.True(plan.Where(c => c.AssetId == favourite.AssetId).Sum(c => c.Length)
                    > plan.Where(c => c.AssetId == plain.AssetId).Sum(c => c.Length));
    }

    [Fact]
    public void AVideoThatCouldNotBeAnalysed_IsStillCut_Blind()
    {
        var analysed = new SummaryCandidate(Guid.NewGuid(), true, Jan1, 60);
        var unreadable = new SummaryCandidate(Guid.NewGuid(), true, Jan1.AddDays(1), 60);
        var profiles = new Dictionary<Guid, VideoProfile> { [analysed.AssetId] = Lively(60, 10, 20) };

        var plan = SummaryPlanner.Plan(new[] { analysed, unreadable }, false,
            new SummaryPlanOptions { TargetSeconds = 20 }, profiles);

        Assert.Contains(plan, c => c.AssetId == unreadable.AssetId);
        Assert.Contains(plan, c => c.AssetId == analysed.AssetId);
    }

    [Fact]
    public void TheShortlist_IsBiggerThanWhatFits_AndCoversTheYear()
    {
        var videos = Enumerable.Range(0, 365)
            .Select(d => new SummaryCandidate(Guid.NewGuid(), true, Jan1.AddDays(d), 30))
            .ToList();

        var shortlist = SummaryPlanner.VideosToAnalyze(videos, false, new SummaryPlanOptions { TargetSeconds = 60 });

        Assert.Equal(23, shortlist.Count); // 60 s / 4 s base × 1.5
        Assert.Equal(1, shortlist.First().CapturedAt.Month);
        Assert.Equal(12, shortlist.Last().CapturedAt.Month);
    }

    [Fact]
    public void FitToBudget_TrimsTheLongestCuts()
    {
        var clips = new List<SummaryClip>
        {
            new(Guid.NewGuid(), true, Jan1, 0, 12),
            new(Guid.NewGuid(), true, Jan1, 0, 5),
        };

        var fitted = SummaryPlanner.FitToBudget(clips, 14, 3);

        Assert.Equal(14, fitted.Sum(c => c.Length), 3);
        Assert.Equal(9, fitted[0].Length, 3);
        Assert.Equal(5, fitted[1].Length, 3);
    }
}
