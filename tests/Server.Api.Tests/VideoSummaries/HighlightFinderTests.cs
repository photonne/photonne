using Photonne.Server.Api.Features.VideoSummaries.Highlights;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// Where the good moments of a video are, from made-up profiles: the busy,
/// loud, sharp, lit stretch wins; cuts land on shot changes and pauses rather
/// than mid-sentence; short videos go in whole; a long allowance becomes
/// several moments.
/// </summary>
public sealed class HighlightFinderTests
{
    /// <summary>A profile from one reading per second.</summary>
    internal static VideoProfile Profile(
        int seconds,
        Func<int, (double? Motion, double? Bright, double? Blur, double? Loud)> at,
        params double[] sceneCuts)
    {
        var readings = Enumerable.Range(0, seconds).Select(at).ToArray();
        return new VideoProfile(
            seconds,
            readings.Select(r => r.Motion).ToArray(),
            readings.Select(r => r.Bright).ToArray(),
            readings.Select(r => r.Blur).ToArray(),
            readings.Select(r => r.Loud).ToArray(),
            sceneCuts);
    }

    /// <summary>A steady, quiet shot…</summary>
    private static (double?, double?, double?, double?) Calm(int _) => (2, 120, 4, -35);

    /// <summary>…and the moment: movement and someone laughing.</summary>
    private static (double?, double?, double?, double?) Lively(int _) => (8, 120, 3, -18);

    [Fact]
    public void ThePeak_IsPicked_NotTheMiddle()
    {
        // A 60 s video whose good part is 6 s near the start.
        var profile = Profile(60, s => s is >= 8 and < 14 ? Lively(s) : Calm(s));

        var segment = Assert.Single(HighlightFinder.Pick(profile, 6));

        Assert.InRange(segment.Start, 6.5, 8.5);
        Assert.InRange(segment.End, 13.5, 15.5);
    }

    [Fact]
    public void Cuts_LandOnPausesAndShotChanges_Outward()
    {
        // Talking from 10 to 20 (quiet before and after), a shot change at 9.
        var profile = Profile(40,
            s => s is >= 10 and < 20 ? (5, 120, 3, -18) : (5, 120, 3, -60),
            sceneCuts: 9);

        var segment = Assert.Single(HighlightFinder.Pick(profile, 8));

        // Starts where the talking does (or the shot change just before),
        // and doesn't stop until it's over.
        Assert.InRange(segment.Start, 9, 10);
        Assert.Equal(20, segment.End, 3);
    }

    [Fact]
    public void Breaks_AreTheEnds_ShotChanges_AndWhereTheSoundComesAndGoes()
    {
        var profile = Profile(12, s => s is >= 4 and < 8 ? (5, 120, 3, -20) : (5, 120, 3, -70), sceneCuts: 2.4);

        Assert.Equal(new[] { 0, 2.4, 4, 8, 12 }, HighlightFinder.Breaks(profile));
    }

    [Fact]
    public void ShortVideos_GoInWhole()
    {
        var profile = Profile(6, Lively);

        var segment = Assert.Single(HighlightFinder.Pick(profile, 5));

        Assert.Equal(0, segment.Start);
        Assert.Equal(6, segment.Length);
    }

    [Fact]
    public void ALongAllowance_BecomesSeveralMoments_InTimeOrder()
    {
        // Three separate good moments in a 3-minute video.
        var profile = Profile(180, s => s is (>= 20 and < 30) or (>= 90 and < 100) or (>= 150 and < 160) ? Lively(s) : Calm(s));

        var segments = HighlightFinder.Pick(profile, 30);

        Assert.Equal(3, segments.Count);
        Assert.Equal(segments.OrderBy(s => s.Start), segments);
        Assert.Contains(segments, s => s.Start < 30 && s.End > 20);
        Assert.Contains(segments, s => s.Start < 100 && s.End > 90);
        Assert.Contains(segments, s => s.Start < 160 && s.End > 150);
        Assert.All(segments.Zip(segments.Skip(1)), pair => Assert.True(pair.First.End <= pair.Second.Start));
    }

    [Fact]
    public void BlackAndFrozenStretches_AreAvoided()
    {
        // Lens cap on for 20 s, then a frozen frame, then an ordinary shot.
        var profile = Profile(60, s => s switch
        {
            < 20 => (6, 10, null, -20),
            < 40 => (0, 120, 3, -20),
            _ => (4, 120, 3, -20),
        });

        var segment = Assert.Single(HighlightFinder.Pick(profile, 5));

        Assert.True(segment.Start >= 40, $"picked {segment.Start}");
    }

    [Fact]
    public void Interest_RanksALivelyVideoAboveADullOne()
    {
        var lively = Profile(30, s => s is >= 10 and < 16 ? Lively(s) : Calm(s));
        var dull = Profile(30, Calm);

        Assert.True(HighlightFinder.Interest(lively) > HighlightFinder.Interest(dull) + 0.1);
    }
}
