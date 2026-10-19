using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// Which stretch of a video makes it into a summary: the lively, loud, sharp
/// seconds rather than black, frozen or the camera going up; longer for a
/// better moment; whole for a short video; a second moment for a long one;
/// and the cut on a shot change or a pause rather than mid-sentence.
/// </summary>
public sealed class SummaryMomentsTests
{
    /// <summary>A calm, sharp, lit video with quiet sound, <paramref name="seconds"/> long.</summary>
    private static VideoSignals Calm(int seconds, bool audio = true) => new(
        seconds,
        Enumerable.Repeat(1f, seconds).ToArray(),
        Enumerable.Repeat(120f, seconds).ToArray(),
        Enumerable.Repeat(5f, seconds).ToArray(),
        audio ? Enumerable.Repeat(-35f, seconds).ToArray() : null,
        Array.Empty<float>());

    private static VideoSignals With(VideoSignals s, int from, int to, float? motion = null, float? loudness = null,
        float? brightness = null, float? blur = null)
    {
        for (var i = from; i < to; i++)
        {
            if (motion is { } m) s.Motion[i] = m;
            if (loudness is { } l) s.Loudness![i] = l;
            if (brightness is { } b) s.Brightness[i] = b;
            if (blur is { } u) s.Blur[i] = u;
        }
        return s;
    }

    [Fact]
    public void Score_RewardsMovementAndLoudPeaks_AndZeroesBlack()
    {
        var signals = With(With(With(Calm(10), 2, 3, motion: 10), 5, 6, loudness: -15), 8, 9, brightness: 5);

        var scores = SummaryMoments.Score(signals);

        Assert.True(scores[2] > scores[4] + 0.2, "movement");
        Assert.True(scores[5] > scores[4] + 0.2, "a laugh or a cheer");
        Assert.Equal(0, scores[8]);
    }

    [Fact]
    public void Score_PenalisesFrozenPictures_AndTheCameraGoingUp()
    {
        var signals = With(With(Calm(12), 5, 6, motion: 0), 0, 2, motion: 8);
        var steady = With(Calm(12), 6, 8, motion: 8);

        var scores = SummaryMoments.Score(signals);
        var steadyScores = SummaryMoments.Score(steady);

        Assert.True(scores[5] < scores[4] * 0.5, "frozen");
        // The same jolt counts half at the start: it's the phone being raised.
        Assert.True(scores[0] < steadyScores[6] * 0.6);
    }

    [Fact]
    public void Find_TakesTheLivelyStretch_NotTheMiddle()
    {
        var signals = With(Calm(40), 30, 35, motion: 12, loudness: -15);

        var moment = SummaryMoments.Find(40, signals)[0];

        Assert.True(moment.Start <= 30 && moment.End >= 35, $"{moment.Start}-{moment.End}");
    }

    [Fact]
    public void Find_GivesLongerMoments_ToMoreInterestingVideos()
    {
        var dull = SummaryMoments.Find(40, Calm(40))[0];
        var lively = SummaryMoments.Find(40, With(Calm(40), 10, 30, motion: 12, loudness: -15))[0];

        Assert.InRange(dull.Length, 3, 6);
        Assert.InRange(lively.Length, 10, 12);
        Assert.True(lively.Interest > dull.Interest);
    }

    [Fact]
    public void Find_KeepsShortVideosWhole()
    {
        var moment = Assert.Single(SummaryMoments.Find(6.5, Calm(7)));

        Assert.Equal(0, moment.Start);
        Assert.Equal(6.5, moment.Length);
    }

    [Fact]
    public void Find_GivesALongLivelyVideo_MoreThanOneMoment_ApartFromEachOther()
    {
        var signals = With(With(Calm(90), 15, 22, motion: 12, loudness: -15), 60, 68, motion: 12, loudness: -15);

        var moments = SummaryMoments.Find(90, signals);

        Assert.True(moments.Count >= 2, $"{moments.Count} moments");
        var ordered = moments.OrderBy(m => m.Start).ToList();
        for (var i = 1; i < ordered.Count; i++)
            Assert.True(ordered[i].Start >= ordered[i - 1].End, "moments don't overlap");
        Assert.Contains(moments, m => m.Start <= 16 && m.End >= 21);
        Assert.Contains(moments, m => m.Start <= 61 && m.End >= 67);
        // A calm video of the same length gives just the one.
        Assert.Single(SummaryMoments.Find(90, Calm(90)));
    }

    [Fact]
    public void Find_WithoutSignals_TakesAMomentALittleBeforeTheMiddle()
    {
        var moment = Assert.Single(SummaryMoments.Find(60, null));

        Assert.InRange(moment.Start + moment.Length / 2, 20, 28);
        Assert.InRange(moment.Length, 3, 12);
    }

    [Fact]
    public void Snap_MovesTheCutsToAShotChange_ElseToAPause()
    {
        var withCut = Calm(30) with { SceneCuts = new[] { 9.2f } };
        var withPause = With(With(Calm(30), 0, 30, loudness: -20), 19, 20, loudness: -60);

        var (start, _) = SummaryMoments.Snap(10, 18, withCut, 30, new MomentOptions());
        var (_, end) = SummaryMoments.Snap(10, 18, withPause, 30, new MomentOptions());

        Assert.Equal(9.2, start, 3);
        Assert.Equal(19.5, end, 3);
    }

    [Fact]
    public void Snap_LeavesTheCut_WhenNothingNaturalIsNear()
    {
        var far = Calm(30) with { SceneCuts = new[] { 3f } };

        Assert.Equal((10.0, 18.0), SummaryMoments.Snap(10, 18, far, 30, new MomentOptions()));
    }
}
