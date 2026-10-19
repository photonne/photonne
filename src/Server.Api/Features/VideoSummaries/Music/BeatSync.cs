using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Features.VideoSummaries.Music;

/// <summary>
/// Nudges clip lengths so each cut lands on a beat of the music. With
/// crossfades of length <c>x</c>, clip <c>i</c> starts at the sum of the
/// previous lengths minus <c>x</c> each, and its cut is felt at the middle of
/// the crossfade into the next one; that moment is moved to the nearest beat,
/// by stretching or trimming the clip within what its source allows, by no
/// more than about half a beat. Pure.
/// </summary>
public static class BeatSync
{
    /// <param name="available">How long each clip could at most run (a video's
    /// remaining length from its start; anything for a photo).</param>
    public static List<SummaryClip> Align(
        IReadOnlyList<SummaryClip> clips, IReadOnlyList<double> available, Beats beats,
        double crossfade, double offset, double minLength = 2)
    {
        var result = new List<SummaryClip>(clips.Count);
        var start = offset; // the first clip starts after the title card, if any
        for (var i = 0; i < clips.Count; i++)
        {
            var clip = clips[i];
            var length = clip.Length;
            if (i < clips.Count - 1)
            {
                var cut = start + length - crossfade / 2;
                var beat = Nearest(beats, cut);
                if (beat is { } b && Math.Abs(b - cut) <= beats.Period * 0.6)
                {
                    var wanted = b - start + crossfade / 2;
                    if (wanted >= minLength && wanted <= available[i]) length = wanted;
                }
            }
            result.Add(clip with { Length = Math.Round(length, 3) });
            start += length - crossfade;
        }
        return result;
    }

    private static double? Nearest(Beats beats, double at)
    {
        double? best = null;
        foreach (var t in beats.Times)
        {
            if (best == null || Math.Abs(t - at) < Math.Abs(best.Value - at)) best = t;
            if (t > at + beats.Period) break;
        }
        return best;
    }
}
