using System.Runtime.InteropServices;

namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// One person seen in a video: the mean of their face embeddings across the
/// sampled frames, and when they're on screen. Who they are is decided per
/// user at planning time (<see cref="PeopleIndex"/>), because naming people
/// is private to each user.
/// </summary>
/// <param name="Embedding">L2-normalised mean ArcFace embedding, base64 float32.</param>
/// <param name="Times">Sampled frame times (s) the person appears in.</param>
/// <param name="MaxArea">Largest share of the frame their face took.</param>
public sealed record FaceTrack(string Embedding, double[] Times, double MaxArea);

/// <summary>
/// What the ML service saw in a video's sampled frames and heard in its
/// audio. Each part is null when that capability wasn't available at analysis
/// time, so it can be filled in later without redoing the rest.
/// </summary>
/// <param name="FrameTimes">When each sampled frame was taken (s).</param>
/// <param name="Faces">People seen; null if faces weren't analysed.</param>
/// <param name="Quality">Per frame: CLIP similarity to a well-made photo minus
/// to a badly-made one. Raw: only meaningful compared within the video.</param>
/// <param name="Embedding">Mean CLIP embedding of the frames (base64 float32):
/// what the video looks like, to spot two near-identical videos.</param>
/// <param name="Speech">Stretches with someone speaking, [start, end] s; empty
/// when the video has no sound, null if voice wasn't analysed.</param>
public sealed record VideoMlProfile(
    double[] FrameTimes,
    List<FaceTrack>? Faces,
    double[]? Quality,
    string? Embedding,
    List<double[]>? Speech)
{
    /// <summary>Bump when what's measured changes, so cached profiles are redone.</summary>
    public const int Version = 1;

    /// <summary>True when every capability was analysed: nothing to fill in later.</summary>
    public bool IsComplete => Faces != null && Quality != null && Speech != null;
}

/// <summary>Float vectors as compact strings for the jsonb cache.</summary>
public static class Vectors
{
    public static string Encode(float[] vector) => Convert.ToBase64String(MemoryMarshal.AsBytes(vector.AsSpan()));

    public static float[] Decode(string encoded) =>
        MemoryMarshal.Cast<byte, float>(Convert.FromBase64String(encoded)).ToArray();

    public static float[] Normalize(float[] v)
    {
        var norm = Math.Sqrt(v.Sum(x => (double)x * x));
        return norm > 0 ? v.Select(x => (float)(x / norm)).ToArray() : v;
    }

    /// <summary>Cosine similarity of two L2-normalised vectors.</summary>
    public static double Similarity(float[] a, float[] b)
    {
        if (a.Length != b.Length || a.Length == 0) return 0;
        double dot = 0;
        for (var i = 0; i < a.Length; i++) dot += a[i] * b[i];
        return dot;
    }

    /// <summary>L2-normalised mean.</summary>
    public static float[] Mean(IReadOnlyCollection<float[]> vectors)
    {
        var dim = vectors.First().Length;
        var sum = new float[dim];
        foreach (var v in vectors)
        {
            for (var i = 0; i < dim && i < v.Length; i++) sum[i] += v[i];
        }
        return Normalize(sum);
    }
}
