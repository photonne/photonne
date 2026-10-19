namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// What the ML service adds to a video's per-second picture, for one user:
/// whether one of their named people is on screen, whether anyone is, how
/// well-made the shot looks, and whether someone is speaking. Arrays are one
/// entry per second of the <see cref="VideoProfile"/>.
/// </summary>
/// <param name="Named">1 where a person the user named is on screen.</param>
/// <param name="Faces">1 where someone's face is on screen.</param>
/// <param name="Quality">0–1, relative to the video's own best and worst frames;
/// null when not analysed or flat.</param>
/// <param name="Speech">Someone is speaking; null when not analysed.</param>
public sealed record MomentSignals(double[] Named, double[] Faces, double[]? Quality, bool[]? Speech)
{
    /// <summary>The signals of a video for a user, or null without any ML data.</summary>
    public static MomentSignals? Build(VideoMlProfile? ml, int seconds, PeopleIndex people)
    {
        if (ml == null || seconds <= 0) return null;
        var times = ml.FrameTimes;
        var step = times.Length > 1 ? times[1] - times[0] : 2;

        var named = new double[seconds];
        var faces = new double[seconds];
        foreach (var track in ml.Faces ?? new List<FaceTrack>())
        {
            var isNamed = people.Count > 0 && people.IsNamedPerson(Vectors.Decode(track.Embedding));
            foreach (var t in track.Times)
            {
                // A frame stands for the seconds around it, up to the next one.
                var from = Math.Max(0, (int)Math.Floor(t - step / 2));
                var to = Math.Min(seconds - 1, (int)Math.Ceiling(t + step / 2) - 1);
                for (var s = from; s <= to; s++)
                {
                    faces[s] = 1;
                    if (isNamed) named[s] = 1;
                }
            }
        }

        double[]? quality = null;
        if (ml.Quality is { Length: > 0 } raw && raw.Length == times.Length)
        {
            var min = raw.Min();
            var max = raw.Max();
            if (max - min >= 0.01)
            {
                quality = new double[seconds];
                for (var s = 0; s < seconds; s++)
                {
                    var nearest = 0;
                    for (var i = 1; i < times.Length; i++)
                    {
                        if (Math.Abs(times[i] - (s + 0.5)) < Math.Abs(times[nearest] - (s + 0.5))) nearest = i;
                    }
                    quality[s] = (raw[nearest] - min) / (max - min);
                }
            }
        }

        bool[]? speech = null;
        if (ml.Speech != null)
        {
            speech = new bool[seconds];
            for (var s = 0; s < seconds; s++)
            {
                var overlap = ml.Speech.Sum(seg => Math.Max(0, Math.Min(seg[1], s + 1) - Math.Max(seg[0], s)));
                speech[s] = overlap >= 0.3;
            }
        }

        return new MomentSignals(named, faces, quality, speech);
    }
}
