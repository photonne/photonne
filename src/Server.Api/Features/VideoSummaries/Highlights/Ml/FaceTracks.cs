namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// Groups the faces found in a video's frames into people: a face joins the
/// track whose mean it's most similar to when that's close enough to be the
/// same person, otherwise it starts a new one. Faces too small or too unsure
/// to say anything are left out. Pure.
/// </summary>
public static class FaceTracks
{
    /// <summary>ArcFace cosine similarity above which two faces in the same
    /// video are taken as the same person (frames of one video are much more
    /// alike than photos years apart).</summary>
    public const double SamePerson = 0.5;

    private const float MinScore = 0.6f;
    private const float MinArea = 0.002f;

    public static List<FaceTrack> Build(IReadOnlyList<(double Time, IReadOnlyList<FrameFace> Faces)> frames)
    {
        var tracks = new List<(List<float[]> Members, List<double> Times, double MaxArea, float[] Mean)>();
        foreach (var (time, faces) in frames)
        {
            foreach (var face in faces.Where(f => f.Score >= MinScore && f.Area >= MinArea))
            {
                var embedding = Vectors.Normalize(face.Embedding);
                var best = -1;
                var bestSimilarity = SamePerson;
                for (var i = 0; i < tracks.Count; i++)
                {
                    var similarity = Vectors.Similarity(tracks[i].Mean, embedding);
                    if (similarity >= bestSimilarity)
                    {
                        best = i;
                        bestSimilarity = similarity;
                    }
                }

                if (best < 0)
                {
                    tracks.Add((new List<float[]> { embedding }, new List<double> { time }, face.Area, embedding));
                    continue;
                }

                var track = tracks[best];
                track.Members.Add(embedding);
                if (!track.Times.Contains(time)) track.Times.Add(time);
                tracks[best] = (track.Members, track.Times, Math.Max(track.MaxArea, face.Area), Vectors.Mean(track.Members));
            }
        }

        return tracks
            .Select(t => new FaceTrack(Vectors.Encode(t.Mean), t.Times.OrderBy(x => x).ToArray(), Math.Round(t.MaxArea, 4)))
            .ToList();
    }
}
