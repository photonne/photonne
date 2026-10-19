namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>A stretch of a video to use: <see cref="Length"/> seconds from <see cref="Start"/>.</summary>
public sealed record HighlightSegment(double Start, double Length)
{
    public double End => Start + Length;
}

public sealed record HighlightOptions
{
    /// <summary>Shorter than this a moment doesn't land.</summary>
    public double MinSegment { get; init; } = 3;

    /// <summary>Longer than this a single moment drags; a long allowance is
    /// spent on several moments instead.</summary>
    public double MaxSegment { get; init; } = 12;

    public int MaxSegmentsPerVideo { get; init; } = 3;

    /// <summary>Videos this short go in whole: they already are one moment.</summary>
    public double WholeVideoSeconds { get; init; } = 8;

    /// <summary>How far a cut may move to land on a shot change or a pause.</summary>
    public double SnapSeconds { get; init; } = 1.5;

    /// <summary>How far a cut that would land while someone is talking may
    /// reach to the end (or start) of what they're saying.</summary>
    public double SpeechReachSeconds { get; init; } = 4;
}

/// <summary>
/// Finds the best moments of a video from its <see cref="VideoProfile"/>.
///
/// Every second gets a score from what was measured: movement (none is a
/// frozen frame, too much a shaking camera), sound louder than the video's own
/// usual level (laughter, cheering, someone calling out), sharpness, and not
/// being black. The first and last seconds are discounted: they are usually
/// the phone being raised and lowered.
///
/// The best-scoring windows are taken, and then their edges move to the
/// nearest natural break — a shot change or a pause in the sound — so a cut
/// doesn't land in the middle of a sentence or a movement. Pure: tested on
/// made-up profiles.
/// </summary>
public static class HighlightFinder
{
    private const double SilentLufs = -45;

    /// <summary>The score of each second of the video, 0–1.</summary>
    public static double[] Score(VideoProfile profile, MomentSignals? signals = null)
    {
        var n = profile.Seconds;
        var loudness = profile.Loudness.Where(l => l.HasValue).Select(l => l!.Value).ToList();
        var hasAudio = loudness.Count > 0;
        var median = hasAudio ? Median(loudness) : 0;

        var scores = new double[n];
        for (var i = 0; i < n; i++)
        {
            var y = profile.Motion[i];
            double motion = y switch
            {
                null => 0.3,
                < 0.2 => 0, // frozen
                > 30 => 0.4, // shaking or whip-panning
                _ => Math.Clamp(y.Value / 8, 0, 1),
            };

            var b = profile.Blur[i];
            var sharp = b is null ? 0.6 : 1 - Math.Clamp((b.Value - 3) / 6, 0, 1);

            double raw;
            if (hasAudio)
            {
                var l = profile.Loudness[i];
                // Relative to the video's own level: a quiet video still has
                // its louder moments, and a loud one isn't all highlight.
                var sound = l is null ? 0 : Math.Clamp((l.Value - median + 3) / 10, 0, 1);
                raw = 0.45 * motion + 0.35 * sound + 0.2 * sharp;
            }
            else
            {
                raw = 0.65 * motion + 0.35 * sharp;
            }

            var bright = profile.Brightness[i];
            var light = bright switch { null => 1, < 25 => 0.1, < 40 => 0.6, _ => 1.0 };
            var shake = y > 30 ? 0.7 : 1;
            var edge = i == 0 ? 0.6 : i == n - 1 ? 0.7 : 1;

            if (signals != null)
            {
                // What ML adds, on top of the measurements (which keep most of
                // the weight, so a frame isn't great just for having a face):
                // the people the user named matter most; any face, someone
                // speaking and a well-made shot a little. A busy second can
                // already score 1 on measurements alone, so the base is scaled
                // down rather than the bonus added on top and clamped away.
                var quality = signals.Quality != null ? At(signals.Quality, i) : 0.5;
                var speaking = signals.Speech is { } speech && i < speech.Length && speech[i] ? 1 : 0;
                raw = Math.Clamp(
                    0.7 * raw + 0.18 * At(signals.Named, i) + 0.05 * At(signals.Faces, i)
                    + 0.04 * speaking + 0.06 * quality,
                    0, 1);
            }

            scores[i] = raw * light * shake * edge;
        }
        return scores;
    }

    /// <summary>How good the video's best few seconds are, 0–1: what decides
    /// which videos get more time in the summary.</summary>
    public static double Interest(VideoProfile profile, MomentSignals? signals = null, double window = 4)
    {
        var scores = Score(profile, signals);
        var length = Math.Min(window, profile.Duration);
        if (length <= 0) return 0;
        var cumulative = Cumulative(scores);
        var best = 0.0;
        for (var start = 0.0; start + length <= profile.Duration + 1e-9; start += 0.5)
            best = Math.Max(best, Mean(cumulative, start, length));
        return best;
    }

    /// <summary>Where a cut can fall naturally: the ends, shot changes, and
    /// where the sound goes quiet or comes back.</summary>
    public static List<double> Breaks(VideoProfile profile, MomentSignals? signals = null) =>
        new[] { 0, profile.Duration }
            .Concat(profile.SceneCuts)
            .Concat(SoundBreaks(profile, signals))
            .Where(b => b >= 0 && b <= profile.Duration)
            .Distinct()
            .OrderBy(b => b)
            .ToList();

    /// <summary>Per second: is something being heard (talking, laughing), as
    /// opposed to silence or the video's background hum. All false without sound.</summary>
    internal static bool[] Sounding(VideoProfile profile, MomentSignals? signals = null)
    {
        // With voice detection, "being heard" means someone speaking: wind,
        // music or traffic no longer hold a cut open.
        if (signals?.Speech is { } speech && speech.Length == profile.Seconds) return speech;

        var loudness = profile.Loudness.Where(l => l.HasValue).Select(l => l!.Value).ToList();
        if (loudness.Count == 0) return new bool[profile.Seconds];
        var median = Median(loudness);
        return profile.Loudness
            .Select(l => l is { } v && v >= SilentLufs && v >= median - 15)
            .ToArray();
    }

    /// <summary>Where the sound comes or goes, or changes level sharply (a
    /// voice stopping over background noise is a 10 dB drop, not silence).</summary>
    private static IEnumerable<double> SoundBreaks(VideoProfile profile, MomentSignals? signals = null)
    {
        var sounding = Sounding(profile, signals);
        for (var i = 1; i < sounding.Length; i++)
        {
            var jump = profile.Loudness[i] is { } now && profile.Loudness[i - 1] is { } before
                && Math.Abs(now - before) >= 10;
            if (sounding[i] != sounding[i - 1] || jump) yield return i;
        }
    }

    /// <summary>
    /// The moments to use from this video, spending about
    /// <paramref name="seconds"/> in total: one segment, or several (each up
    /// to <see cref="HighlightOptions.MaxSegment"/>) for a long allowance. In
    /// time order. Snapping to breaks can make the total come out slightly
    /// different from what was asked.
    /// </summary>
    public static List<HighlightSegment> Pick(
        VideoProfile profile, double seconds, HighlightOptions? options = null, MomentSignals? signals = null)
    {
        var o = options ?? new HighlightOptions();
        var duration = profile.Duration;
        if (duration <= 0 || seconds <= 0) return new List<HighlightSegment>();
        if (seconds >= duration - 0.25 || (duration <= o.WholeVideoSeconds && seconds >= duration * 0.75))
            return new List<HighlightSegment> { new(0, duration) };

        var count = (int)Math.Ceiling(seconds / o.MaxSegment);
        count = Math.Clamp(count, 1, o.MaxSegmentsPerVideo);
        count = Math.Max(1, Math.Min(count, (int)Math.Floor(seconds / o.MinSegment)));
        var length = Math.Min(seconds / count, duration);

        var cumulative = Cumulative(Score(profile, signals));
        var breaks = Breaks(profile, signals);
        var sounding = Sounding(profile, signals);
        var soundBreaks = SoundBreaks(profile, signals).Append(duration).Prepend(0).ToList();
        var chosen = new List<HighlightSegment>();

        for (var k = 0; k < count; k++)
        {
            HighlightSegment? best = null;
            var bestScore = double.MinValue;
            for (var start = 0.0; start + length <= duration + 1e-9; start += 0.5)
            {
                var candidate = new HighlightSegment(start, length);
                if (chosen.Any(c => Overlaps(c, candidate, gap: 1))) continue;
                var score = Mean(cumulative, start, length);
                if (score > bestScore + 1e-9)
                {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null) break;

            var snapped = KeepSpeechWhole(Snap(best, breaks, duration, o), sounding, soundBreaks, duration, o);
            chosen.Add(chosen.Any(c => Overlaps(c, snapped, gap: 0)) ? best : snapped);
        }

        return chosen.OrderBy(c => c.Start).ToList();
    }

    /// <summary>Moves the start back (or slightly forward) and the end forward
    /// (or slightly back) onto the nearest break within reach. Moving outward
    /// is preferred: better a second more than a sentence cut short.</summary>
    internal static HighlightSegment Snap(HighlightSegment segment, List<double> breaks, double duration, HighlightOptions o)
    {
        double? Nearest(double at, double before, double after, bool preferEarlier)
        {
            double? pick = null;
            var cost = double.MaxValue;
            foreach (var b in breaks)
            {
                if (b < at - before - 1e-9 || b > at + after + 1e-9) continue;
                var distance = Math.Abs(b - at);
                // Moving the "wrong" way (inward) counts double.
                var inward = preferEarlier ? b > at : b < at;
                var c = inward ? distance * 2 : distance;
                if (c < cost)
                {
                    cost = c;
                    pick = b;
                }
            }
            return pick;
        }

        var start = Nearest(segment.Start, o.SnapSeconds, 0.5, preferEarlier: true) ?? segment.Start;
        var end = Nearest(segment.End, 0.5, o.SnapSeconds, preferEarlier: false) ?? segment.End;
        start = Math.Clamp(start, 0, duration);
        end = Math.Clamp(end, 0, duration);

        if (end - start < Math.Min(o.MinSegment, segment.Length) - 1e-9) return segment;
        return new HighlightSegment(Math.Round(start, 3), Math.Round(end - start, 3));
    }

    /// <summary>
    /// A cut that still lands while something is being heard reaches out to
    /// where it stops (or, for the start, where it began) — within
    /// <see cref="HighlightOptions.SpeechReachSeconds"/> and never past
    /// <see cref="HighlightOptions.MaxSegment"/>. Continuous sound (music,
    /// a waterfall) has no such end in reach and is left as it is.
    /// </summary>
    internal static HighlightSegment KeepSpeechWhole(
        HighlightSegment segment, bool[] sounding, List<double> soundBreaks, double duration, HighlightOptions o)
    {
        bool SoundingAt(double t)
        {
            var i = (int)Math.Floor(t);
            return i >= 0 && i < sounding.Length && sounding[i];
        }

        var start = segment.Start;
        var end = segment.End;
        var limit = Math.Max(o.MaxSegment, segment.Length);

        bool OnBreak(double t) => soundBreaks.Any(b => Math.Abs(b - t) < 0.05);

        // The second just before the end is still being heard, and the one after too.
        if (end < duration && !OnBreak(end) && SoundingAt(end - 0.01) && SoundingAt(end + 0.01))
        {
            var stop = soundBreaks.Where(b => b > end && b <= end + o.SpeechReachSeconds).DefaultIfEmpty(-1).Min();
            if (stop > 0 && stop - start <= limit + 1e-9) end = stop;
        }
        if (start > 0 && !OnBreak(start) && SoundingAt(start - 0.01) && SoundingAt(start + 0.01))
        {
            var begin = soundBreaks.Where(b => b < start && b >= start - o.SpeechReachSeconds).DefaultIfEmpty(-1).Max();
            if (begin >= 0 && end - begin <= limit + 1e-9) start = begin;
        }

        return new HighlightSegment(Math.Round(start, 3), Math.Round(end - start, 3));
    }

    private static double At(double[] values, int i) => i < values.Length ? values[i] : 0;

    private static bool Overlaps(HighlightSegment a, HighlightSegment b, double gap) =>
        a.Start < b.End + gap && b.Start < a.End + gap;

    /// <summary>Running total of the per-second scores: C[i] is the sum of the first i seconds.</summary>
    private static double[] Cumulative(double[] scores)
    {
        var c = new double[scores.Length + 1];
        for (var i = 0; i < scores.Length; i++) c[i + 1] = c[i] + scores[i];
        return c;
    }

    /// <summary>Mean score over [start, start + length), seconds being steps.</summary>
    private static double Mean(double[] cumulative, double start, double length)
    {
        double At(double t)
        {
            var n = cumulative.Length - 1;
            if (t <= 0) return 0;
            if (t >= n) return cumulative[n];
            var i = (int)Math.Floor(t);
            return cumulative[i] + (cumulative[i + 1] - cumulative[i]) * (t - i);
        }
        return length <= 0 ? 0 : (At(start + length) - At(start)) / length;
    }

    private static double Median(List<double> values)
    {
        var sorted = values.OrderBy(v => v).ToList();
        var mid = sorted.Count / 2;
        return sorted.Count % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }
}
