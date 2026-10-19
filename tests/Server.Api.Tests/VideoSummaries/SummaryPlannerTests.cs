using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// What goes into a summary: videos first, photos only when asked and within
/// their share; every part of the chronology covered when there's too much;
/// the livelier moments (and favourites) getting the time left — and never
/// past the target.
/// </summary>
public sealed class SummaryPlannerTests
{
    private static readonly DateTime Jan1 = new(2024, 1, 1, 12, 0, 0);

    private static SummaryCandidate Video(int day, double seconds, VideoSignals? signals = null, bool favorite = false) =>
        new(Guid.NewGuid(), true, Jan1.AddDays(day), seconds, favorite, signals);

    private static SummaryCandidate Photo(int day) =>
        new(Guid.NewGuid(), false, Jan1.AddDays(day), 0);

    /// <summary>Signals of a video that is calm except for a lively stretch.</summary>
    private static VideoSignals Signals(int seconds, int liveFrom = 0, int liveTo = 0)
    {
        var motion = Enumerable.Range(0, seconds).Select(i => i >= liveFrom && i < liveTo ? 12f : 1f).ToArray();
        var loudness = Enumerable.Range(0, seconds).Select(i => i >= liveFrom && i < liveTo ? -15f : -35f).ToArray();
        return new VideoSignals(seconds, motion, Enumerable.Repeat(120f, seconds).ToArray(),
            Enumerable.Repeat(5f, seconds).ToArray(), loudness, Array.Empty<float>());
    }

    [Fact]
    public void FewVideos_AllGoIn_InChronologicalOrder()
    {
        var late = Video(200, 20);
        var early = Video(10, 20);
        var middle = Video(100, 20);

        var plan = SummaryPlanner.Plan(new[] { late, early, middle }, includePhotos: false);

        Assert.Equal(new[] { early.AssetId, middle.AssetId, late.AssetId }, plan.Select(c => c.AssetId));
        Assert.All(plan, c => Assert.InRange(c.Length, 3, 12));
        // Without signals, taken a little before the middle, not from the very start.
        Assert.All(plan, c => Assert.InRange(c.Start + c.Length / 2, 5, 11));
    }

    [Fact]
    public void ManyVideos_CoverTheWholeYear_WithinTheTarget()
    {
        var videos = Enumerable.Range(0, 365).Select(day => Video(day, 30)).ToList();

        var plan = SummaryPlanner.Plan(videos, includePhotos: false);

        Assert.True(plan.Sum(c => c.Length) <= 300 + 1e-6);
        Assert.True(plan.Sum(c => c.Length) >= 290, "the target is filled");
        Assert.True(plan.First().CapturedAt.Month == 1, "the sample starts in January");
        Assert.True(plan.Last().CapturedAt.Month == 12, "and reaches December");
        // No month is left out.
        Assert.Equal(12, plan.Select(c => c.CapturedAt.Month).Distinct().Count());
        Assert.Equal(plan.OrderBy(c => c.CapturedAt).Select(c => c.AssetId), plan.Select(c => c.AssetId));
    }

    [Fact]
    public void LivelyVideos_GetMoreTime_ThanDullOnes()
    {
        var lively = Enumerable.Range(0, 100).Select(day => Video(day * 2, 40, Signals(40, 10, 30))).ToList();
        var dull = Enumerable.Range(0, 100).Select(day => Video(day * 2 + 1, 40, Signals(40))).ToList();

        var plan = SummaryPlanner.Plan(lively.Concat(dull), includePhotos: false);

        var livelyIds = lively.Select(v => v.AssetId).ToHashSet();
        var livelySeconds = plan.Where(c => livelyIds.Contains(c.AssetId)).Sum(c => c.Length);
        var dullSeconds = plan.Where(c => !livelyIds.Contains(c.AssetId)).Sum(c => c.Length);
        Assert.True(livelySeconds > dullSeconds * 2, $"{livelySeconds} s lively vs {dullSeconds} s dull");
        Assert.True(plan.Sum(c => c.Length) <= 300 + 1e-6);
    }

    [Fact]
    public void Favorites_GoFirst_AmongEquals()
    {
        var videos = Enumerable.Range(0, 200).Select(day => Video(day, 30, Signals(30), favorite: day % 10 == 0)).ToList();

        var plan = SummaryPlanner.Plan(videos, includePhotos: false, new SummaryPlanOptions { TargetSeconds = 120 });

        var favorites = videos.Where(v => v.IsFavorite).Select(v => v.AssetId).ToHashSet();
        Assert.Subset(plan.Select(c => c.AssetId).ToHashSet(), favorites);
    }

    [Fact]
    public void ALongLivelyVideo_GivesMoreThanOneMoment_WhenThereIsTime()
    {
        var signals = Signals(90, 15, 22);
        for (var i = 60; i < 68; i++)
        {
            signals.Motion[i] = 12;
            signals.Loudness![i] = -15;
        }
        var video = Video(1, 90, signals);

        var plan = SummaryPlanner.Plan(new[] { video, Video(2, 30) }, includePhotos: false);

        var cuts = plan.Where(c => c.AssetId == video.AssetId).ToList();
        Assert.True(cuts.Count >= 2, $"{cuts.Count} cuts");
        Assert.Equal(cuts.OrderBy(c => c.Start), cuts);
    }

    [Fact]
    public void ShortVideos_GoInWhole_AndFlashesAreSkipped()
    {
        var shortClip = Video(1, 2);
        var flash = Video(2, 1);

        var plan = SummaryPlanner.Plan(new[] { shortClip, flash }, includePhotos: false);

        var clip = Assert.Single(plan);
        Assert.Equal(shortClip.AssetId, clip.AssetId);
        Assert.Equal(0, clip.Start);
        Assert.Equal(2, clip.Length);
    }

    [Fact]
    public void Photos_AreLeftOut_UnlessAskedFor()
    {
        var candidates = new[] { Video(1, 30), Photo(2), Photo(3) };

        Assert.All(SummaryPlanner.Plan(candidates, includePhotos: false), c => Assert.True(c.IsVideo));
        Assert.Equal(2, SummaryPlanner.Plan(candidates, includePhotos: true).Count(c => !c.IsVideo));
    }

    [Fact]
    public void Photos_KeepToTheirShare_WhenVideosCanFillTheRest()
    {
        var videos = Enumerable.Range(0, 200).Select(day => Video(day, 30));
        var photos = Enumerable.Range(0, 500).Select(i => Photo(i % 365));

        var plan = SummaryPlanner.Plan(videos.Concat(photos), includePhotos: true);

        var photoSeconds = plan.Where(c => !c.IsVideo).Sum(c => c.Length);
        Assert.Equal(120, photoSeconds); // 40 % of 300 s
        Assert.True(plan.Where(c => c.IsVideo).Sum(c => c.Length) >= 170, "videos fill the remaining 180 s");
        Assert.True(plan.Sum(c => c.Length) <= 300 + 1e-6);
    }

    [Fact]
    public void Photos_TakeWhatVideosCannotFill()
    {
        var videos = new[] { Video(1, 20), Video(2, 20) };
        var photos = Enumerable.Range(0, 500).Select(i => Photo(i % 365));

        var plan = SummaryPlanner.Plan(videos.Concat(photos), includePhotos: true);

        // Both videos go in; photos fill the rest of the 5 minutes.
        var videoSeconds = plan.Where(c => c.IsVideo).Sum(c => c.Length);
        Assert.Equal(2, plan.Count(c => c.IsVideo));
        Assert.Equal((int)Math.Floor((300 - videoSeconds) / 3), plan.Count(c => !c.IsVideo));
        Assert.True(plan.Sum(c => c.Length) <= 300 + 1e-6);
    }

    [Fact]
    public void OnlyPhotos_MakeASlideshow()
    {
        var plan = SummaryPlanner.Plan(Enumerable.Range(0, 10).Select(Photo), includePhotos: true);

        Assert.Equal(10, plan.Count);
        Assert.All(plan, c => Assert.Equal(3, c.Length));
    }

    [Fact]
    public void NothingUsable_PlansNothing()
    {
        Assert.Empty(SummaryPlanner.Plan(Array.Empty<SummaryCandidate>(), includePhotos: true));
        Assert.Empty(SummaryPlanner.Plan(new[] { Photo(1) }, includePhotos: false));
    }
}
