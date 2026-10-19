using Photonne.Server.Api.Features.VideoSummaries.Highlights;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;
using static Photonne.Server.Api.Tests.VideoSummaries.HighlightFinderTests;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// What the ML service adds to a summary, on made-up data: faces grouped into
/// people and recognised as the user's named ones, per-second signals, the
/// named people winning the cut, voice (not noise) holding a cut open, and
/// repeated videos and photo bursts going in once.
/// </summary>
public sealed class VideoMlTests
{
    private static readonly DateTime Day = new(2024, 6, 1, 12, 0, 0);

    /// <summary>A unit vector pointing mostly along one axis, with a little of another.</summary>
    internal static float[] Direction(int axis, int nudge = -1, float amount = 0.1f, int dim = 512)
    {
        var v = new float[dim];
        v[axis] = 1;
        if (nudge >= 0) v[nudge] = amount;
        return Vectors.Normalize(v);
    }

    private static FrameFace Face(float[] embedding, float area = 0.05f, float score = 0.9f) => new(area, score, embedding);

    [Fact]
    public void Faces_AreGroupedIntoPeople_AcrossFrames()
    {
        var ana = Direction(0);
        var leo = Direction(1);
        var frames = new List<(double, IReadOnlyList<FrameFace>)>
        {
            (1, new[] { Face(Direction(0, 5)), Face(leo) }),
            (3, new[] { Face(Direction(0, 6)) }),
            (5, new[] { Face(ana), Face(leo, area: 0.0005f), Face(Direction(2), score: 0.3f) }),
        };

        var tracks = FaceTracks.Build(frames);

        Assert.Equal(2, tracks.Count); // tiny and unsure faces don't count
        var anaTrack = tracks.Single(t => Vectors.Similarity(Vectors.Decode(t.Embedding), ana) > 0.9);
        Assert.Equal(new double[] { 1, 3, 5 }, anaTrack.Times);
        Assert.Equal(new double[] { 1 }, tracks.Single(t => t != anaTrack).Times);
    }

    [Fact]
    public void NamedPeople_AreRecognised_OthersAreNot()
    {
        var people = new PeopleIndex(new Dictionary<Guid, float[]> { [Guid.NewGuid()] = Direction(0) });

        Assert.True(people.IsNamedPerson(Direction(0, 7)));
        Assert.False(people.IsNamedPerson(Direction(1)));
        Assert.False(PeopleIndex.Empty.IsNamedPerson(Direction(0)));
    }

    [Fact]
    public void Signals_SpreadFrameReadingsOverSeconds()
    {
        var named = Direction(0);
        var ml = new VideoMlProfile(
            FrameTimes: new double[] { 1, 3, 5, 7, 9 },
            Faces: new List<FaceTrack>
            {
                new(Vectors.Encode(named), new double[] { 7, 9 }, 0.05),
                new(Vectors.Encode(Direction(1)), new double[] { 1 }, 0.05),
            },
            Quality: new[] { 0.0, 0.0, 0.2, 0.1, 0.0 },
            Embedding: null,
            Speech: new List<double[]> { new[] { 2.0, 4.5 } });
        var people = new PeopleIndex(new Dictionary<Guid, float[]> { [Guid.NewGuid()] = named });

        var signals = MomentSignals.Build(ml, 10, people)!;

        Assert.Equal(new double[] { 0, 0, 0, 0, 0, 0, 1, 1, 1, 1 }, signals.Named);
        Assert.Equal(new double[] { 1, 1, 0, 0, 0, 0, 1, 1, 1, 1 }, signals.Faces);
        Assert.Equal(1, signals.Quality![5]); // the frame at 5 s is the video's best
        Assert.Equal(0, signals.Quality[0]);
        Assert.Equal(new[] { false, false, true, true, true, false, false, false, false, false }, signals.Speech);
    }

    [Fact]
    public void WithoutMl_ThereAreNoSignals()
    {
        Assert.Null(MomentSignals.Build(null, 10, PeopleIndex.Empty));
    }

    [Fact]
    public void ANamedPerson_WinsTheCut_OverAnEquallyLivelyStretch()
    {
        // Two equally lively stretches; Martina is in the second.
        var profile = Profile(60, s => s is (>= 10 and < 16) or (>= 40 and < 46) ? (8, 120, 3, -18) : (2, 120, 4, -35));
        var named = new double[60];
        for (var s = 40; s < 46; s++) named[s] = 1;
        var signals = new MomentSignals(named, named, null, null);

        var plain = Assert.Single(HighlightFinder.Pick(profile, 6));
        var withPeople = Assert.Single(HighlightFinder.Pick(profile, 6, signals: signals));

        Assert.True(plain.Start < 20);
        Assert.InRange(withPeople.Start, 38.5, 41);
    }

    [Fact]
    public void Voice_HoldsACutOpen_ButBackgroundNoiseDoesNot()
    {
        // Loud all along (music), someone speaks from 10 to 20.
        var profile = Profile(40, _ => (5, 120, 3, -18));
        var speech = Enumerable.Range(0, 40).Select(s => s is >= 10 and < 20).ToArray();
        var withVoice = new MomentSignals(new double[40], new double[40], null, speech);

        var segment = Assert.Single(HighlightFinder.Pick(profile, 8, signals: withVoice));

        Assert.InRange(segment.Start, 9, 10);
        Assert.Equal(20, segment.End, 3);
    }

    [Fact]
    public void TheSameMomentFilmedTwice_GoesInOnce_TheBetterTake()
    {
        var look = Direction(0);
        var better = new SummaryCandidate(Guid.NewGuid(), true, Day, 60);
        var worse = new SummaryCandidate(Guid.NewGuid(), true, Day.AddMinutes(1), 60);
        var other = new SummaryCandidate(Guid.NewGuid(), true, Day.AddMinutes(2), 60);
        var analysed = new Dictionary<Guid, AnalysedVideo>
        {
            [better.AssetId] = new(Profile(60, s => s is >= 20 and < 30 ? (8, 120, 3, -18) : (2, 120, 4, -35)), Embedding: look),
            [worse.AssetId] = new(Profile(60, _ => (2, 120, 4, -35)), Embedding: Direction(0, 3)),
            [other.AssetId] = new(Profile(60, _ => (2, 120, 4, -35)), Embedding: Direction(1)),
        };

        var plan = SummaryPlanner.Plan(new[] { better, worse, other }, false,
            new SummaryPlanOptions { TargetSeconds = 60 }, analysed);
        var used = plan.Select(c => c.AssetId).ToHashSet();

        Assert.Contains(better.AssetId, used);
        Assert.DoesNotContain(worse.AssetId, used);
        Assert.Contains(other.AssetId, used);
    }

    [Fact]
    public void ABurst_GoesInAsOnePhoto_TheFavouriteIfThereIsOne()
    {
        var look = Direction(0);
        var burst = Enumerable.Range(0, 5)
            .Select(i => new SummaryCandidate(Guid.NewGuid(), false, Day.AddSeconds(i), 0, IsFavorite: i == 3, Embedding: Direction(0, 4 + i, 0.05f)))
            .ToList();
        var later = new SummaryCandidate(Guid.NewGuid(), false, Day.AddHours(2), 0, Embedding: look);
        var different = new SummaryCandidate(Guid.NewGuid(), false, Day.AddSeconds(10), 0, Embedding: Direction(1));

        var kept = SummaryPlanner.WithoutRepeats(
            burst.Append(different).Append(later).OrderBy(c => c.CapturedAt).ToList(), new SummaryPlanOptions());

        Assert.Equal(new[] { burst[3].AssetId, different.AssetId, later.AssetId }, kept.Select(c => c.AssetId));
    }

    [Fact]
    public void Vectors_RoundTripThroughTheirStringForm()
    {
        var v = Direction(3, 9, 0.4f);

        Assert.Equal(v, Vectors.Decode(Vectors.Encode(v)));
    }

    [Fact]
    public void Frames_AreSampledEveryTwoSecondsOrMore_UpToTwenty()
    {
        Assert.Equal(new[] { 1.0, 3, 5 }, VideoMlAnalyzer.FrameTimes(6));
        var long_ = VideoMlAnalyzer.FrameTimes(600);
        Assert.Equal(20, long_.Length);
        Assert.Equal(15, long_[0], 3);
    }
}
