using Photonne.Server.Api.Features.VideoSummaries.Highlights;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>A source asset the summary could draw from.</summary>
/// <param name="DurationSeconds">Probed length for videos; ignored for photos.</param>
/// <param name="IsFavorite">The user's own mark of what matters: gets more time
/// and is the last to be left out.</param>
/// <param name="Embedding">For photos: the CLIP embedding (normalised), to
/// keep one shot of a burst instead of five.</param>
public sealed record SummaryCandidate(
    Guid AssetId, bool IsVideo, DateTime CapturedAt, double DurationSeconds, bool IsFavorite = false,
    float[]? Embedding = null);

/// <summary>A video with what's known about it: its measurements, and when the
/// ML service looked at it, the per-user signals and what it looks like.</summary>
public sealed record AnalysedVideo(VideoProfile Profile, MomentSignals? Signals = null, float[]? Embedding = null);

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

    /// <summary>With analysed videos: how many more videos than fit at the
    /// base cut get analysed, so the duller ones can be left out.</summary>
    public double ShortlistFactor { get; init; } = 1.5;

    /// <summary>With analysed videos: share of the video budget planned
    /// before cuts snap to natural breaks, which tends to lengthen them.</summary>
    public double SnapSlack { get; init; } = 0.92;

    /// <summary>How a video's allowance is spent on moments.</summary>
    public HighlightOptions Highlights { get; init; } = new();

    /// <summary>CLIP similarity above which two videos (taken within
    /// <see cref="DuplicateVideoWindow"/>) are the same moment filmed twice.</summary>
    public double DuplicateVideoSimilarity { get; init; } = 0.93;

    public TimeSpan DuplicateVideoWindow { get; init; } = TimeSpan.FromDays(2);

    /// <summary>CLIP similarity above which two photos (taken within
    /// <see cref="BurstWindow"/>) are the same shot: a burst, or the "one more".</summary>
    public double DuplicatePhotoSimilarity { get; init; } = 0.92;

    public TimeSpan BurstWindow { get; init; } = TimeSpan.FromMinutes(30);
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
///
/// With the videos' <see cref="VideoProfile"/>s (see <see cref="VideosToAnalyze"/>)
/// the cut stops being blind: a shortlist is analysed, the duller videos are
/// left out, time goes to each video by how good its best moments are (more
/// for favourites), and <see cref="HighlightFinder"/> picks which moments —
/// one or several per video, cut at natural breaks. A video whose analysis
/// failed falls back to the blind cut.
/// </summary>
public static class SummaryPlanner
{
    /// <summary>The videos worth analysing for this summary: an even sample
    /// across the chronology, bigger than what fits, plus favourites. Plan with
    /// their profiles and the same arguments.</summary>
    public static List<SummaryCandidate> VideosToAnalyze(
        IEnumerable<SummaryCandidate> candidates, bool includePhotos, SummaryPlanOptions? options = null)
    {
        var o = options ?? new SummaryPlanOptions();
        var (videos, _, videoBudget) = Split(candidates, includePhotos, o);
        return Shortlist(videos, videoBudget, o);
    }

    public static List<SummaryClip> Plan(
        IEnumerable<SummaryCandidate> candidates,
        bool includePhotos,
        SummaryPlanOptions? options,
        IReadOnlyDictionary<Guid, VideoProfile>? profiles) =>
        Plan(candidates, includePhotos, options,
            profiles?.ToDictionary(p => p.Key, p => new AnalysedVideo(p.Value)));

    public static List<SummaryClip> Plan(
        IEnumerable<SummaryCandidate> candidates,
        bool includePhotos,
        SummaryPlanOptions? options = null,
        IReadOnlyDictionary<Guid, AnalysedVideo>? profiles = null)
    {
        var o = options ?? new SummaryPlanOptions();
        var (videos, chosenPhotos, videoBudget) = Split(candidates, includePhotos, o);

        var clips = profiles is { Count: > 0 }
            ? PlanHighlights(videos, videoBudget, profiles, o)
            : PlanBlind(videos, videoBudget, o);
        clips.AddRange(chosenPhotos.Select(p => new SummaryClip(p.AssetId, false, p.CapturedAt, 0, o.PhotoSeconds)));

        return clips
            .OrderBy(c => c.CapturedAt)
            .ThenBy(c => c.AssetId)
            .ThenBy(c => c.Start)
            .ToList();
    }

    /// <summary>The usable videos, the photos that go in, and the time left for videos.</summary>
    private static (List<SummaryCandidate> Videos, List<SummaryCandidate> Photos, double VideoBudget) Split(
        IEnumerable<SummaryCandidate> candidates, bool includePhotos, SummaryPlanOptions o)
    {
        var ordered = candidates
            .OrderBy(c => c.CapturedAt)
            .ThenBy(c => c.AssetId)
            .ToList();

        var videos = ordered
            .Where(c => c.IsVideo && c.DurationSeconds >= o.MinVideoSeconds)
            .ToList();
        var photos = includePhotos
            ? WithoutRepeats(ordered.Where(c => !c.IsVideo).ToList(), o)
            : new List<SummaryCandidate>();

        // What videos could take at most, every one of them at its longest cut:
        // photos only get past their share when videos can't fill the rest.
        var videosAtMost = videos.Sum(v => Math.Min(o.MaxVideoSeconds, v.DurationSeconds));

        var photoBudget = Math.Min(
            photos.Count * o.PhotoSeconds,
            Math.Max(o.TargetSeconds * o.MaxPhotoShare, o.TargetSeconds - videosAtMost));
        var photoCount = (int)Math.Floor(photoBudget / o.PhotoSeconds + 1e-9);
        var chosenPhotos = SampleEvenly(photos, photoCount);

        var videoBudget = o.TargetSeconds - chosenPhotos.Count * o.PhotoSeconds;
        return (videos, chosenPhotos, videoBudget);
    }

    /// <summary>The blind cut: base cuts for an even sample, grown evenly,
    /// each a little before the video's middle.</summary>
    private static List<SummaryClip> PlanBlind(List<SummaryCandidate> videos, double videoBudget, SummaryPlanOptions o)
    {
        double BaseCut(SummaryCandidate v) => Math.Min(o.BaseVideoSeconds, v.DurationSeconds);
        var chosenVideos = ChooseVideos(videos, videoBudget, BaseCut);
        var lengths = GrowCuts(chosenVideos, videoBudget, BaseCut, o.MaxVideoSeconds);

        var clips = new List<SummaryClip>(chosenVideos.Count);
        for (var i = 0; i < chosenVideos.Count; i++)
            clips.Add(BlindClip(chosenVideos[i], lengths[i]));
        return clips;
    }

    private static SummaryClip BlindClip(SummaryCandidate v, double length)
    {
        var start = Math.Max(0, (v.DurationSeconds - length) * 0.4);
        return new SummaryClip(v.AssetId, true, v.CapturedAt, Math.Round(start, 3), Math.Round(length, 3));
    }

    /// <summary>An even sample of the videos, <see cref="SummaryPlanOptions.ShortlistFactor"/>
    /// times what fits at the base cut, plus the favourites among them.</summary>
    private static List<SummaryCandidate> Shortlist(List<SummaryCandidate> videos, double videoBudget, SummaryPlanOptions o)
    {
        if (videos.Count == 0 || videoBudget <= 0) return new List<SummaryCandidate>();
        var fits = (int)Math.Ceiling(videoBudget / o.BaseVideoSeconds * o.ShortlistFactor);
        var favorites = videos.Where(v => v.IsFavorite).ToList();
        return SampleEvenly(videos, fits)
            .Concat(SampleEvenly(favorites, Math.Max(1, fits / 3)))
            .Distinct()
            .OrderBy(v => v.CapturedAt)
            .ThenBy(v => v.AssetId)
            .ToList();
    }

    /// <summary>
    /// Time by merit: every shortlisted video starts at its base cut; while
    /// that's over budget, the least interesting goes; what's left is shared
    /// out by interest (squared, so a great video gets clearly more than a
    /// decent one) up to a few moments per video. Each allowance is then spent
    /// by <see cref="HighlightFinder.Pick"/>.
    /// </summary>
    private static List<SummaryClip> PlanHighlights(
        List<SummaryCandidate> videos,
        double videoBudget,
        IReadOnlyDictionary<Guid, AnalysedVideo> analysed,
        SummaryPlanOptions o)
    {
        var shortlist = Shortlist(videos, videoBudget, o);
        var h = o.Highlights;
        var items = shortlist.Select(v =>
        {
            var video = analysed.GetValueOrDefault(v.AssetId);
            var profile = video?.Profile;
            // Unanalysed videos rank below average rather than being dropped first.
            var interest = profile != null ? HighlightFinder.Interest(profile, video!.Signals) : 0.3;
            var weight = Math.Max(0.05, interest) * (v.IsFavorite ? 1.4 : 1);
            var min = Math.Min(o.BaseVideoSeconds, v.DurationSeconds);
            var max = profile != null
                ? Math.Min(v.DurationSeconds, h.MaxSegment * h.MaxSegmentsPerVideo)
                : Math.Min(o.MaxVideoSeconds, v.DurationSeconds);
            return new Allowance(v, profile, video?.Signals, video?.Embedding, weight, min, max) { Seconds = min };
        }).ToList();

        // The same moment filmed twice: keep the better take.
        var kept = new List<Allowance>();
        foreach (var item in items.OrderByDescending(i => i.Weight))
        {
            var repeat = item.Embedding != null && kept.Any(k =>
                k.Embedding != null
                && (k.Video.CapturedAt - item.Video.CapturedAt).Duration() <= o.DuplicateVideoWindow
                && Vectors.Similarity(k.Embedding, item.Embedding) >= o.DuplicateVideoSimilarity);
            if (!repeat) kept.Add(item);
        }
        items = items.Where(kept.Contains).ToList();

        var budget = videoBudget * o.SnapSlack;
        while (items.Count > 0 && items.Sum(i => i.Seconds) > budget + 1e-9)
        {
            var dullest = items.OrderBy(i => i.Weight).ThenByDescending(i => i.Video.CapturedAt).First();
            items.Remove(dullest);
        }

        var spare = budget - items.Sum(i => i.Seconds);
        while (spare > 1e-6)
        {
            var growable = items.Where(i => i.Seconds < i.Max - 1e-9).ToList();
            if (growable.Count == 0) break;
            var total = growable.Sum(i => i.Weight * i.Weight);
            var given = 0.0;
            foreach (var item in growable)
            {
                var add = Math.Min(item.Max - item.Seconds, spare * item.Weight * item.Weight / total);
                item.Seconds += add;
                given += add;
            }
            spare -= given;
            if (given < 1e-6) break;
        }

        var clips = new List<SummaryClip>();
        foreach (var item in items)
        {
            if (item.Profile == null)
            {
                clips.Add(BlindClip(item.Video, item.Seconds));
                continue;
            }
            clips.AddRange(HighlightFinder.Pick(item.Profile, item.Seconds, h, item.Signals)
                .Select(s => new SummaryClip(item.Video.AssetId, true, item.Video.CapturedAt,
                    Math.Round(s.Start, 3), Math.Round(s.Length, 3))));
        }

        return FitToBudget(clips, videoBudget, h.MinSegment);
    }

    /// <summary>
    /// The user's "include always": each pinned asset the plan left out goes
    /// in — a video with its best moment (its base cut, a little longer when
    /// analysed), a photo for its usual time — and makes room by pushing out
    /// the unpinned clips closest to it in time, so the rest of the chronology
    /// keeps its share. Only when nothing unpinned is left are the pinned cuts
    /// themselves trimmed.
    /// </summary>
    public static List<SummaryClip> WithPinned(
        List<SummaryClip> plan,
        IEnumerable<SummaryCandidate> pinned,
        SummaryPlanOptions? options = null,
        IReadOnlyDictionary<Guid, AnalysedVideo>? analysed = null)
    {
        var o = options ?? new SummaryPlanOptions();
        var pins = pinned.ToList();
        if (pins.Count == 0) return plan;

        var clips = plan.ToList();
        var pinnedIds = pins.Select(p => p.AssetId).ToHashSet();
        foreach (var pin in pins.Where(p => clips.All(c => c.AssetId != p.AssetId)))
        {
            if (!pin.IsVideo)
            {
                clips.Add(new SummaryClip(pin.AssetId, false, pin.CapturedAt, 0, o.PhotoSeconds));
                continue;
            }
            if (pin.DurationSeconds <= 0) continue;
            var video = analysed?.GetValueOrDefault(pin.AssetId);
            if (video == null)
            {
                clips.Add(BlindClip(pin, Math.Min(o.BaseVideoSeconds, pin.DurationSeconds)));
                continue;
            }
            var seconds = Math.Min(pin.DurationSeconds, o.BaseVideoSeconds * 1.5);
            var moments = HighlightFinder.Pick(video.Profile, seconds, o.Highlights, video.Signals);
            clips.AddRange(moments.Count > 0
                ? moments.Select(s => new SummaryClip(pin.AssetId, true, pin.CapturedAt,
                    Math.Round(s.Start, 3), Math.Round(s.Length, 3)))
                : new[] { BlindClip(pin, Math.Min(o.BaseVideoSeconds, pin.DurationSeconds)) });
        }

        var added = pins.Where(p => plan.All(c => c.AssetId != p.AssetId)).Select(p => p.CapturedAt).ToList();
        while (clips.Sum(c => c.Length) > o.TargetSeconds + 1e-6)
        {
            var unpinned = clips.Where(c => !pinnedIds.Contains(c.AssetId)).ToList();
            if (unpinned.Count == 0) break;
            var nearest = unpinned
                .OrderBy(c => added.Count == 0 ? 0 : added.Min(t => Math.Abs((c.CapturedAt - t).TotalSeconds)))
                .ThenByDescending(c => c.CapturedAt)
                .First();
            clips.Remove(nearest);
        }
        clips = FitToBudget(clips, o.TargetSeconds, o.MinVideoSeconds);

        return clips
            .OrderBy(c => c.CapturedAt)
            .ThenBy(c => c.AssetId)
            .ThenBy(c => c.Start)
            .ToList();
    }

    /// <summary>Snapping can push the total past the budget; trims the longest
    /// cuts from their end until it fits, never below a moment's minimum.</summary>
    internal static List<SummaryClip> FitToBudget(List<SummaryClip> clips, double budget, double minSegment)
    {
        var excess = clips.Sum(c => c.Length) - budget;
        while (excess > 1e-6)
        {
            var longest = clips
                .Select((c, i) => (c, i))
                .Where(x => x.c.Length > minSegment + 1e-6)
                .OrderByDescending(x => x.c.Length)
                .FirstOrDefault();
            if (longest.c == null)
            {
                // Everything is at its minimum: drop the last-chosen cut instead.
                clips.RemoveAt(clips.Count - 1);
                excess = clips.Sum(c => c.Length) - budget;
                continue;
            }
            var cut = Math.Min(excess, longest.c.Length - minSegment);
            clips[longest.i] = longest.c with { Length = Math.Round(longest.c.Length - cut, 3) };
            excess -= cut;
        }
        return clips;
    }

    private sealed record Allowance(
        SummaryCandidate Video, VideoProfile? Profile, MomentSignals? Signals, float[]? Embedding,
        double Weight, double Min, double Max)
    {
        public double Seconds { get; set; }
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

    /// <summary>
    /// One photo per burst: in time order, a photo that looks the same as the
    /// one kept just before it (and was taken within
    /// <see cref="SummaryPlanOptions.BurstWindow"/>) is skipped — unless it's a
    /// favourite and that one isn't, in which case it takes its place.
    /// </summary>
    internal static List<SummaryCandidate> WithoutRepeats(List<SummaryCandidate> photos, SummaryPlanOptions o)
    {
        var kept = new List<SummaryCandidate>(photos.Count);
        foreach (var photo in photos)
        {
            var last = kept.Count > 0 ? kept[^1] : null;
            var repeat = last?.Embedding != null && photo.Embedding != null
                && photo.CapturedAt - last.CapturedAt <= o.BurstWindow
                && Vectors.Similarity(last.Embedding, photo.Embedding) >= o.DuplicatePhotoSimilarity;
            if (!repeat) kept.Add(photo);
            else if (photo.IsFavorite && !last!.IsFavorite) kept[^1] = photo;
        }
        return kept;
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
