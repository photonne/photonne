namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>A stretch of a video worth showing: <paramref name="Length"/>
/// seconds from <paramref name="Start"/>, and how interesting it is (0-1).</summary>
public sealed record Moment(double Start, double Length, double Interest)
{
    public double End => Start + Length;
}

public sealed record MomentOptions
{
    public double MinSeconds { get; init; } = 3;
    public double MaxSeconds { get; init; } = 12;

    /// <summary>Videos shorter than this go in whole: there is nothing to cut.</summary>
    public double WholeBelowSeconds { get; init; } = 8;

    /// <summary>How far a cut may move to land on a shot change or a pause.</summary>
    public double SnapSeconds { get; init; } = 1.5;
}

/// <summary>
/// Finds a video's best moments from its <see cref="VideoSignals"/>:
///   * every second gets a score (<see cref="Score"/>): movement, loud
///     moments (laughs, cheers) and sharpness count; black, frozen and the
///     shaky first and last seconds (the phone going up or down) don't;
///   * the best second anchors a moment, which grows to 3-12 s depending on
///     how interesting it is (<see cref="Find"/>);
///   * its ends then move to a shot change or a pause in the audio nearby,
///     so it doesn't cut in the middle of a sentence (<see cref="Snap"/>);
///   * a long, lively video gives a second or third moment, apart from the first.
/// Without signals (the analysis failed) every second scores the same and
/// the moment lands a little before the middle, as summaries always did.
/// Pure, so it can be tested without ffmpeg.
/// </summary>
public static class SummaryMoments
{
    /// <summary>Brightness (0-255) under which a second is black.</summary>
    private const float BlackBelow = 16;

    /// <summary>Motion under which a second is a frozen picture.</summary>
    private const float FrozenBelow = 0.15f;

    /// <summary>Seconds at either end where a jolt is the camera, not the scene.</summary>
    private const int EdgeSeconds = 2;

    /// <summary>A further moment must be at least this share of the first one's interest.</summary>
    private const double FurtherMomentShare = 0.75;

    /// <summary>Nor less interesting than this, however good the first was.</summary>
    private const double FurtherMomentFloor = 0.35;

    /// <summary>Moments of the same video keep at least this far apart.</summary>
    private const double MomentGapSeconds = 2;

    /// <summary>How many moments a video of this length may give.</summary>
    public static int MaxMoments(double durationSeconds) =>
        durationSeconds >= 60 ? 3 : durationSeconds >= 25 ? 2 : 1;

    /// <summary>The moments of a video, best first.</summary>
    public static List<Moment> Find(double durationSeconds, VideoSignals? signals, MomentOptions? options = null)
    {
        var o = options ?? new MomentOptions();
        if (durationSeconds <= 0) return new List<Moment>();

        var scores = signals != null && signals.Seconds > 0
            ? Score(signals)
            : Enumerable.Repeat(0.5, Math.Max(1, (int)Math.Ceiling(durationSeconds - 1e-6))).ToArray();

        if (durationSeconds < o.WholeBelowSeconds)
            return new List<Moment> { new(0, Math.Round(durationSeconds, 3), Math.Round(scores.Average(), 3)) };

        // A gentle preference for a little before the middle breaks ties (and is
        // all there is without signals): the start is often the camera going up.
        var smooth = Smooth(scores);
        var ranked = smooth
            .Select((s, i) => s * (1 - 0.1 * Math.Abs((i + 0.5) / smooth.Length - 0.4)))
            .ToArray();

        var moments = new List<Moment>();
        var taken = new bool[smooth.Length];
        // Blind, every stretch is as good as another: one moment, not three.
        var maxMoments = signals == null ? 1 : MaxMoments(durationSeconds);
        for (var k = 0; k < maxMoments; k++)
        {
            var peak = -1;
            for (var i = 0; i < ranked.Length; i++)
                if (!taken[i] && (peak < 0 || ranked[i] > ranked[peak])) peak = i;
            if (peak < 0) break;

            var interest = Math.Clamp(smooth[peak], 0, 1);
            if (k > 0 && (interest < moments[0].Interest * FurtherMomentShare || interest < FurtherMomentFloor)) break;

            var length = Math.Min(durationSeconds, o.MinSeconds + (o.MaxSeconds - o.MinSeconds) * interest);
            var (start, end) = Grow(peak, length, scores, taken, durationSeconds);
            if (end - start < o.MinSeconds - 1e-9)
            {
                // Squeezed between earlier moments: not a moment of its own.
                taken[peak] = true;
                k--;
                if (taken.All(t => t)) break;
                continue;
            }
            (start, end) = Snap(start, end, signals, durationSeconds, o, taken);

            moments.Add(new Moment(Math.Round(start, 3), Math.Round(end - start, 3), Math.Round(interest, 3)));
            var from = Math.Max(0, (int)Math.Floor(start - MomentGapSeconds));
            var to = Math.Min(taken.Length, (int)Math.Ceiling(end + MomentGapSeconds));
            for (var i = from; i < to; i++) taken[i] = true;
        }

        return moments;
    }

    /// <summary>
    /// How worth showing each second is, 0-1. Movement weighs most, then
    /// sound (absolute loudness and peaks over the video's own level: a laugh
    /// in a quiet room counts), then sharpness relative to the rest of the video.
    /// </summary>
    public static double[] Score(VideoSignals signals)
    {
        var n = signals.Seconds;
        var scores = new double[n];

        var blurMin = signals.Blur.Min();
        var blurMax = signals.Blur.Max();
        var loudnessMedian = signals.Loudness is { } l ? Median(l) : 0;
        var motionMedian = Median(signals.Motion);

        for (var i = 0; i < n; i++)
        {
            var motion = Math.Clamp(signals.Motion[i] / 12.0, 0, 1);
            var sharp = blurMax - blurMin > 0.5 ? (blurMax - signals.Blur[i]) / (blurMax - blurMin) : 0.5;

            double score;
            if (signals.Loudness is { } loudness)
            {
                var level = Math.Clamp((loudness[i] + 45) / 30.0, 0, 1);
                var peak = Math.Clamp((loudness[i] - loudnessMedian) / 12.0, 0, 1);
                score = 0.45 * motion + 0.35 * (0.5 * level + 0.5 * peak) + 0.2 * sharp;
            }
            else
            {
                score = 0.7 * motion + 0.3 * sharp;
            }

            if (signals.Brightness[i] < BlackBelow) score = 0;
            else if (i > 0 && signals.Motion[i] < FrozenBelow) score *= 0.3;

            var atEdge = i < EdgeSeconds || i >= n - EdgeSeconds;
            if (atEdge && signals.Motion[i] > Math.Max(1, motionMedian * 1.5)) score *= 0.5;

            scores[i] = Math.Clamp(score, 0, 1);
        }

        return scores;
    }

    /// <summary>
    /// Moves a cut's ends onto natural boundaries within
    /// <see cref="MomentOptions.SnapSeconds"/>: a shot change first, else a
    /// quiet second. The cut stays within its video, clear of other moments
    /// (<paramref name="taken"/>), and within the moment lengths.
    /// </summary>
    internal static (double Start, double End) Snap(
        double start, double end, VideoSignals? signals, double duration, MomentOptions o, bool[]? taken = null)
    {
        if (signals == null) return (start, end);

        var cuts = signals.SceneCuts.Select(c => (double)c).Where(c => c > 0 && c < duration).ToList();
        var pauses = new List<double>();
        if (signals.Loudness is { } loudness)
        {
            var median = Median(loudness);
            var quiet = Math.Max(-45, median - 10);
            for (var i = 0; i < loudness.Length; i++)
                if (loudness[i] <= quiet) pauses.Add(i + 0.5);
        }

        bool Free(double from, double to)
        {
            if (taken == null) return true;
            for (var i = Math.Max(0, (int)Math.Floor(from)); i < Math.Min(taken.Length, (int)Math.Ceiling(to)); i++)
                if (taken[i]) return false;
            return true;
        }

        double Best(double at, Func<double, bool> fits)
        {
            foreach (var candidates in new[] { cuts, pauses })
            {
                var best = candidates
                    .Where(c => Math.Abs(c - at) <= o.SnapSeconds && fits(c))
                    .OrderBy(c => Math.Abs(c - at))
                    .Cast<double?>()
                    .FirstOrDefault();
                if (best is { } b) return b;
            }
            return at;
        }

        bool Fits(double s, double e) =>
            s >= 0 && e <= duration + 1e-9 && e - s >= Math.Min(o.MinSeconds, duration) - 1e-9
            && e - s <= o.MaxSeconds + 1e-9 && Free(s, e);

        // The start lands at the boundary itself: the new shot, the end of a pause.
        var newStart = start > 0 ? Best(start, c => Fits(c, end)) : start;
        var newEnd = end < duration ? Best(end, c => Fits(newStart, c)) : end;
        return (newStart, newEnd);
    }

    /// <summary>Grows a window of <paramref name="length"/> seconds around
    /// second <paramref name="peak"/>, towards the better neighbour each step,
    /// without entering <paramref name="taken"/> seconds or leaving the video.</summary>
    private static (double Start, double End) Grow(int peak, double length, double[] scores, bool[] taken, double duration)
    {
        var first = peak;
        var last = peak; // inclusive
        while (last - first + 1 < length - 1e-9)
        {
            var canLeft = first > 0 && !taken[first - 1];
            var canRight = last < scores.Length - 1 && !taken[last + 1];
            if (!canLeft && !canRight) break;
            // Towards the better neighbour; on a tie, keeping the peak centred.
            var left = canLeft && (!canRight || scores[first - 1] > scores[last + 1]
                || (scores[first - 1] == scores[last + 1] && peak - first <= last - peak));
            if (left) first--;
            else last++;
        }

        // Whole seconds, trimmed to the exact length (and the video's real end).
        var start = (double)first;
        var end = Math.Min(duration, last + 1.0);
        var excess = end - start - length;
        if (excess > 0)
        {
            // Trim from the weaker side.
            var leftWeaker = scores[first] <= scores[last];
            if (leftWeaker) start += excess;
            else end -= excess;
        }
        return (start, end);
    }

    private static double[] Smooth(double[] scores)
    {
        var smooth = new double[scores.Length];
        for (var i = 0; i < scores.Length; i++)
        {
            var from = Math.Max(0, i - 1);
            var to = Math.Min(scores.Length - 1, i + 1);
            double sum = 0;
            for (var j = from; j <= to; j++) sum += scores[j];
            smooth[i] = sum / (to - from + 1);
        }
        return smooth;
    }

    private static double Median(float[] values)
    {
        if (values.Length == 0) return 0;
        var sorted = values.OrderBy(v => v).ToArray();
        var mid = sorted.Length / 2;
        return sorted.Length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }
}
