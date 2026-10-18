namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>A source asset the summary could draw from.</summary>
/// <param name="DurationSeconds">Probed length for videos; ignored for photos.</param>
public sealed record SummaryCandidate(Guid AssetId, bool IsVideo, DateTime CapturedAt, double DurationSeconds);

/// <summary>One cut of the summary: <paramref name="Length"/> seconds of the asset,
/// starting <paramref name="Start"/> seconds in (always 0 for photos).</summary>
public sealed record SummaryClip(Guid AssetId, bool IsVideo, DateTime CapturedAt, double Start, double Length);

public sealed record SummaryPlanOptions
{
    /// <summary>Upper bound for the whole summary. The cut can come out shorter
    /// when there isn't enough material — never padded to reach it.</summary>
    public double TargetSeconds { get; init; } = 300;

    public double PhotoSeconds { get; init; } = 3;

    /// <summary>A video's cut when material is plentiful.</summary>
    public double BaseVideoSeconds { get; init; } = 4;

    /// <summary>How long a video's cut may grow when there are few of them.</summary>
    public double MaxVideoSeconds { get; init; } = 8;

    /// <summary>Clips shorter than this are flashes, not moments; skipped.</summary>
    public double MinVideoSeconds { get; init; } = 1.5;

    /// <summary>Share of the target photos may take while videos could fill it.
    /// Videos are what the user asked a video summary for; photos season it.</summary>
    public double MaxPhotoShare { get; init; } = 0.4;
}

/// <summary>
/// Decides what goes into a summary: which assets, which stretch of each
/// video, and how long each one stays on screen. Pure, so the choice can be
/// tested without ffmpeg.
///
/// The rules, in order:
///   * videos come first; photos (when asked for) get at most
///     <see cref="SummaryPlanOptions.MaxPhotoShare"/> of the target, unless
///     videos can't fill the rest anyway;
///   * when there is more material than time, assets are sampled evenly across
///     the chronology so every part of the year or trip is represented, rather
///     than the first N minutes of it;
///   * when there is less, video cuts grow (up to
///     <see cref="SummaryPlanOptions.MaxVideoSeconds"/>) before the summary
///     settles for being shorter than the target;
///   * a video's cut is taken a little before its middle: the first seconds of
///     a home video tend to be the camera being raised.
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
            .ToList();
        var photos = includePhotos ? ordered.Where(c => !c.IsVideo).ToList() : new List<SummaryCandidate>();

        double BaseCut(SummaryCandidate v) => Math.Min(o.BaseVideoSeconds, v.DurationSeconds);
        // What videos could take at most, every one of them at its longest cut:
        // photos only get past their share when videos can't fill the rest.
        var videosAtMost = videos.Sum(v => Math.Min(o.MaxVideoSeconds, v.DurationSeconds));

        var photoBudget = Math.Min(
            photos.Count * o.PhotoSeconds,
            Math.Max(o.TargetSeconds * o.MaxPhotoShare, o.TargetSeconds - videosAtMost));
        var photoCount = (int)Math.Floor(photoBudget / o.PhotoSeconds + 1e-9);
        var chosenPhotos = SampleEvenly(photos, photoCount);

        var videoBudget = o.TargetSeconds - chosenPhotos.Count * o.PhotoSeconds;
        var chosenVideos = ChooseVideos(videos, videoBudget, BaseCut);
        var lengths = GrowCuts(chosenVideos, videoBudget, BaseCut, o.MaxVideoSeconds);

        var clips = new List<SummaryClip>(chosenVideos.Count + chosenPhotos.Count);
        for (var i = 0; i < chosenVideos.Count; i++)
        {
            var v = chosenVideos[i];
            var length = lengths[i];
            var start = Math.Max(0, (v.DurationSeconds - length) * 0.4);
            clips.Add(new SummaryClip(v.AssetId, true, v.CapturedAt, Math.Round(start, 3), Math.Round(length, 3)));
        }
        clips.AddRange(chosenPhotos.Select(p => new SummaryClip(p.AssetId, false, p.CapturedAt, 0, o.PhotoSeconds)));

        return clips
            .OrderBy(c => c.CapturedAt)
            .ThenBy(c => c.AssetId)
            .ToList();
    }

    /// <summary>All videos when their base cuts fit; otherwise an even sample
    /// across the chronology, as many as fit.</summary>
    private static List<SummaryCandidate> ChooseVideos(
        List<SummaryCandidate> videos, double budget, Func<SummaryCandidate, double> baseCut)
    {
        if (videos.Count == 0 || budget <= 0) return new List<SummaryCandidate>();
        if (videos.Sum(baseCut) <= budget + 1e-9) return videos;

        // Cuts differ (short videos are cut shorter), so find the largest even
        // sample that fits rather than dividing by an average.
        var low = 0;
        var high = videos.Count;
        while (low < high)
        {
            var mid = (low + high + 1) / 2;
            if (SampleEvenly(videos, mid).Sum(baseCut) <= budget + 1e-9) low = mid;
            else high = mid - 1;
        }
        return SampleEvenly(videos, low);
    }

    /// <summary>Spreads whatever budget the base cuts leave over the chosen
    /// videos, a little to each, never past a video's own length or the cap.</summary>
    private static double[] GrowCuts(
        List<SummaryCandidate> videos, double budget, Func<SummaryCandidate, double> baseCut, double maxCut)
    {
        var lengths = videos.Select(baseCut).ToArray();
        var spare = budget - lengths.Sum();

        while (spare > 1e-6)
        {
            var growable = Enumerable.Range(0, videos.Count)
                .Where(i => lengths[i] < Math.Min(maxCut, videos[i].DurationSeconds) - 1e-9)
                .ToList();
            if (growable.Count == 0) break;

            var share = spare / growable.Count;
            foreach (var i in growable)
            {
                var room = Math.Min(maxCut, videos[i].DurationSeconds) - lengths[i];
                var add = Math.Min(room, share);
                lengths[i] += add;
                spare -= add;
            }
        }

        return lengths;
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
