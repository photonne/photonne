namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>A source asset the summary could draw from.</summary>
/// <param name="DurationSeconds">Probed length for videos; ignored for photos.</param>
/// <param name="Signals">How the video looks and sounds second by second; null
/// when it couldn't be measured, and then its moment is taken blind.</param>
public sealed record SummaryCandidate(
    Guid AssetId,
    bool IsVideo,
    DateTime CapturedAt,
    double DurationSeconds,
    bool IsFavorite = false,
    VideoSignals? Signals = null);

/// <summary>One cut of the summary: <paramref name="Length"/> seconds of the asset,
/// starting <paramref name="Start"/> seconds in (always 0 for photos).</summary>
public sealed record SummaryClip(Guid AssetId, bool IsVideo, DateTime CapturedAt, double Start, double Length);

public sealed record SummaryPlanOptions
{
    /// <summary>Upper bound for the whole summary. The cut can come out shorter
    /// when there isn't enough material — never padded to reach it.</summary>
    public double TargetSeconds { get; init; } = 300;

    public double PhotoSeconds { get; init; } = 3;

    /// <summary>Clips shorter than this are flashes, not moments; skipped.</summary>
    public double MinVideoSeconds { get; init; } = 1.5;

    /// <summary>Share of the target photos may take while videos could fill it.
    /// Videos are what the user asked a video summary for; photos season it.</summary>
    public double MaxPhotoShare { get; init; } = 0.4;

    /// <summary>Share of the video time spent covering the whole chronology,
    /// one moment per stretch of it; the rest goes to the best moments left.</summary>
    public double CoverageShare { get; init; } = 0.7;

    /// <summary>Added to a favourite's interest (0-1) when choosing.</summary>
    public double FavoriteBonus { get; init; } = 0.25;

    public MomentOptions Moments { get; init; } = new();
}

/// <summary>
/// Decides what goes into a summary: which assets, which moments of each
/// video (<see cref="SummaryMoments"/>), and how long each one stays on
/// screen. Pure, so the choice can be tested without ffmpeg.
///
/// The rules, in order:
///   * videos come first; photos (when asked for) get at most
///     <see cref="SummaryPlanOptions.MaxPhotoShare"/> of the target, unless
///     videos can't fill the rest anyway;
///   * every video offers its best moment, and a long lively one a second or
///     third; livelier moments are longer (3-12 s), short videos go in whole;
///   * when every video's best moment fits, they all go in; when not, the
///     chronology is split into stretches and each one shows its most
///     interesting video, so every part of the year or trip is represented
///     (<see cref="SummaryPlanOptions.CoverageShare"/> of the time);
///   * the time left goes to the most interesting moments remaining, further
///     moments of chosen videos included; favourites go first.
/// </summary>
public static class SummaryPlanner
{
    public static List<SummaryClip> Plan(IEnumerable<SummaryCandidate> candidates, bool includePhotos, SummaryPlanOptions? options = null)
    {
        var o = options ?? new SummaryPlanOptions();
        var ordered = candidates
            .OrderBy(c => c.CapturedAt)
            .ThenBy(c => c.AssetId)
            .ToList();

        var videos = ordered
            .Where(c => c.IsVideo && c.DurationSeconds >= o.MinVideoSeconds)
            .Select(v => (Video: v, Moments: SummaryMoments.Find(v.DurationSeconds, v.Signals, o.Moments)))
            .Where(v => v.Moments.Count > 0)
            .ToList();
        var photos = includePhotos ? ordered.Where(c => !c.IsVideo).ToList() : new List<SummaryCandidate>();

        // What videos could take at most, every moment of every one of them:
        // photos only get past their share when videos can't fill the rest.
        var videosAtMost = videos.Sum(v => v.Moments.Sum(m => m.Length));

        var photoBudget = Math.Min(
            photos.Count * o.PhotoSeconds,
            Math.Max(o.TargetSeconds * o.MaxPhotoShare, o.TargetSeconds - videosAtMost));
        var photoCount = (int)Math.Floor(photoBudget / o.PhotoSeconds + 1e-9);
        var chosenPhotos = SampleEvenly(photos, photoCount);

        var videoBudget = o.TargetSeconds - chosenPhotos.Count * o.PhotoSeconds;
        var chosenMoments = Allocate(videos, videoBudget, o);

        var clips = chosenMoments
            .Select(c => new SummaryClip(c.Video.AssetId, true, c.Video.CapturedAt, c.Moment.Start, c.Moment.Length))
            .ToList();
        clips.AddRange(chosenPhotos.Select(p => new SummaryClip(p.AssetId, false, p.CapturedAt, 0, o.PhotoSeconds)));

        return clips
            .OrderBy(c => c.CapturedAt)
            .ThenBy(c => c.AssetId)
            .ThenBy(c => c.Start)
            .ToList();
    }

    /// <summary>Which moments fill <paramref name="budget"/> seconds: first
    /// one per stretch of the chronology, then the most interesting left.</summary>
    private static List<(SummaryCandidate Video, Moment Moment)> Allocate(
        List<(SummaryCandidate Video, List<Moment> Moments)> videos, double budget, SummaryPlanOptions o)
    {
        var chosen = new List<(SummaryCandidate Video, Moment Moment)>();
        if (videos.Count == 0 || budget <= 0) return chosen;

        double Priority(SummaryCandidate video, Moment moment) =>
            moment.Interest + (video.IsFavorite ? o.FavoriteBonus : 0);

        // Coverage: every video's best moment when they all fit; otherwise the
        // most stretches of the chronology that fit in the coverage share.
        var picked = new bool[videos.Count];
        var firsts = Enumerable.Range(0, videos.Count).ToList();
        if (videos.Sum(v => v.Moments[0].Length) > budget + 1e-9)
        {
            var coverage = budget * o.CoverageShare;
            firsts = new List<int>();
            for (var k = 1; k <= videos.Count; k++)
            {
                var picks = BucketPicks(videos, k, Priority);
                if (picks.Sum(i => videos[i].Moments[0].Length) > coverage + 1e-9) break;
                firsts = picks;
            }
        }

        var used = 0.0;
        foreach (var i in firsts)
        {
            picked[i] = true;
            chosen.Add((videos[i].Video, videos[i].Moments[0]));
            used += videos[i].Moments[0].Length;
        }

        // The rest by interest: best moments of videos not shown yet, and
        // further moments of the ones that are. A video's moments go in order,
        // so one that doesn't fit holds back its video's lesser ones too.
        var next = picked.Select(p => p ? 1 : 0).ToArray();
        while (true)
        {
            var best = -1;
            for (var i = 0; i < videos.Count; i++)
            {
                if (next[i] >= videos[i].Moments.Count) continue;
                var moment = videos[i].Moments[next[i]];
                if (used + moment.Length > budget + 1e-9) continue;
                if (best < 0 || Priority(videos[i].Video, moment) > Priority(videos[best].Video, videos[best].Moments[next[best]]))
                    best = i;
            }
            if (best < 0) break;

            var chosenMoment = videos[best].Moments[next[best]];
            chosen.Add((videos[best].Video, chosenMoment));
            used += chosenMoment.Length;
            next[best]++;
        }

        return chosen;
    }

    /// <summary>Splits the videos (in chronological order) into
    /// <paramref name="count"/> stretches and picks the one whose best moment
    /// is most interesting in each.</summary>
    private static List<int> BucketPicks(
        List<(SummaryCandidate Video, List<Moment> Moments)> videos, int count,
        Func<SummaryCandidate, Moment, double> priority)
    {
        var picks = new List<int>(count);
        for (var b = 0; b < count; b++)
        {
            var from = (int)Math.Floor((double)b * videos.Count / count);
            var to = (int)Math.Floor((double)(b + 1) * videos.Count / count);
            var best = from;
            for (var i = from + 1; i < to; i++)
                if (priority(videos[i].Video, videos[i].Moments[0]) > priority(videos[best].Video, videos[best].Moments[0]))
                    best = i;
            picks.Add(best);
        }
        return picks;
    }

    /// <summary><paramref name="count"/> items evenly spaced over
    /// <paramref name="items"/> (all of them when it asks for as many or more),
    /// keeping their order.</summary>
    internal static List<T> SampleEvenly<T>(IReadOnlyList<T> items, int count)
    {
        if (count <= 0) return new List<T>();
        if (count >= items.Count) return items.ToList();

        var picked = new List<T>(count);
        for (var i = 0; i < count; i++)
        {
            var index = (int)Math.Floor((i + 0.5) * items.Count / count);
            picked.Add(items[index]);
        }
        return picked;
    }
}
