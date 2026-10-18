using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// What goes into a summary: videos first, photos only when asked and within
/// their share, an even sample across the chronology when there's too much,
/// longer cuts when there's too little — and never past the target.
/// </summary>
public sealed class SummaryPlannerTests
{
    private static readonly DateTime Jan1 = new(2024, 1, 1, 12, 0, 0);

    private static SummaryCandidate Video(int day, double seconds) =>
        new(Guid.NewGuid(), true, Jan1.AddDays(day), seconds);

    private static SummaryCandidate Photo(int day) =>
        new(Guid.NewGuid(), false, Jan1.AddDays(day), 0);

    [Fact]
    public void FewVideos_AllGoIn_InChronologicalOrder_WithLongerCuts()
    {
        var late = Video(200, 60);
        var early = Video(10, 60);
        var middle = Video(100, 60);

        var plan = SummaryPlanner.Plan(new[] { late, early, middle }, includePhotos: false);

        Assert.Equal(new[] { early.AssetId, middle.AssetId, late.AssetId }, plan.Select(c => c.AssetId));
        // Few videos for five minutes: every cut grows to the cap.
        Assert.All(plan, c => Assert.Equal(8, c.Length));
        // Taken a little before the middle, not from the very start.
        Assert.All(plan, c => Assert.Equal((60 - 8) * 0.4, c.Start, 3));
    }

    [Fact]
    public void ManyVideos_AreSampledEvenlyAcrossTheYear_WithinTheTarget()
    {
        var videos = Enumerable.Range(0, 365).Select(day => Video(day, 30)).ToList();

        var plan = SummaryPlanner.Plan(videos, includePhotos: false);

        Assert.True(plan.Sum(c => c.Length) <= 300 + 1e-6);
        Assert.Equal(75, plan.Count); // 300 s at the 4 s base cut
        Assert.True(plan.First().CapturedAt.Month == 1, "the sample starts in January");
        Assert.True(plan.Last().CapturedAt.Month == 12, "and reaches December");
        Assert.Equal(plan.OrderBy(c => c.CapturedAt).Select(c => c.AssetId), plan.Select(c => c.AssetId));
    }

    [Fact]
    public void ShortVideos_AreCutToTheirLength_AndFlashesAreSkipped()
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
        Assert.Equal(45, plan.Count(c => c.IsVideo)); // the remaining 180 s at 4 s
        Assert.True(plan.Sum(c => c.Length) <= 300 + 1e-6);
    }

    [Fact]
    public void Photos_TakeWhatVideosCannotFill()
    {
        var videos = new[] { Video(1, 60), Video(2, 60) };
        var photos = Enumerable.Range(0, 500).Select(i => Photo(i % 365));

        var plan = SummaryPlanner.Plan(videos.Concat(photos), includePhotos: true);

        // Both videos at their longest cut; photos fill the rest of the 5 minutes.
        Assert.All(plan.Where(c => c.IsVideo), c => Assert.Equal(8, c.Length));
        Assert.Equal(94, plan.Count(c => !c.IsVideo)); // (300 - 16) / 3
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
