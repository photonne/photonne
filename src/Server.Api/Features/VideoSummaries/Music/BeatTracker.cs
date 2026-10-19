using System.Globalization;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Features.VideoSummaries.Music;

/// <summary>The beat of a track: when each beat falls, and how far apart they are.</summary>
public sealed record Beats(double[] Times, double Period)
{
    public double Bpm => 60 / Period;
}

/// <summary>
/// Finds the beat of a music track, so the summary can cut on it.
///
/// A classic, small tracker: the onset strength (how much louder each 10 ms
/// gets than the one before) shows where notes start; its autocorrelation
/// shows the spacing they repeat at, searched between 60 and 180 BPM with a
/// gentle preference for around 120; the beat's phase is where onsets line up
/// best with that spacing. Good enough for pop, rock and most music people put
/// under home videos; on a track with no clear beat it still returns a steady
/// grid, which only makes the cuts regular.
/// </summary>
public static class BeatTracker
{
    public const int SampleRate = 8000;
    private const int Hop = 80; // 10 ms
    private const double FramesPerSecond = (double)SampleRate / Hop;

    /// <summary>Decodes up to <paramref name="seconds"/> of the track and tracks its beat.</summary>
    public static async Task<Beats?> TrackAsync(string path, double seconds, string workDirectory, CancellationToken ct)
    {
        Directory.CreateDirectory(workDirectory);
        var raw = Path.Combine(workDirectory, $"music-{Guid.NewGuid():N}.f32");
        try
        {
            var error = await FfmpegProcess.RunFfmpegAsync(new[]
            {
                "-t", seconds.ToString("0.###", CultureInfo.InvariantCulture), "-i", path, "-vn",
                "-ac", "1", "-ar", SampleRate.ToString(CultureInfo.InvariantCulture), "-f", "f32le", raw,
            }, ct);
            if (error != null || !File.Exists(raw)) return null;

            var bytes = await File.ReadAllBytesAsync(raw, ct);
            var samples = new float[bytes.Length / 4];
            Buffer.BlockCopy(bytes, 0, samples, 0, samples.Length * 4);
            return Track(samples);
        }
        finally
        {
            File.Delete(raw);
        }
    }

    /// <summary>Beat of mono samples at <see cref="SampleRate"/>; null when too short or silent.</summary>
    public static Beats? Track(float[] samples)
    {
        var onset = OnsetStrength(samples);
        if (onset.Length < FramesPerSecond * 4 || onset.Max() <= 0) return null;

        var minLag = (int)Math.Round(FramesPerSecond * 60 / 180);
        var maxLag = (int)Math.Round(FramesPerSecond * 60 / 60);
        var bestLag = minLag;
        var bestScore = double.MinValue;
        for (var lag = minLag; lag <= maxLag; lag++)
        {
            double sum = 0;
            for (var i = lag; i < onset.Length; i++) sum += onset[i] * onset[i - lag];
            var bpm = 60 * FramesPerSecond / lag;
            // Log-Gaussian prior around 120 BPM, wide: picks 120 over 60 or 240
            // for a track that fits all three, without overriding a clear tempo.
            var prior = Math.Exp(-0.5 * Math.Pow(Math.Log2(bpm / 120) / 1.0, 2));
            var score = sum / (onset.Length - lag) * prior;
            if (score > bestScore)
            {
                bestScore = score;
                bestLag = lag;
            }
        }

        var bestPhase = 0;
        var bestPhaseScore = double.MinValue;
        for (var phase = 0; phase < bestLag; phase++)
        {
            double sum = 0;
            for (var i = phase; i < onset.Length; i += bestLag) sum += onset[i];
            if (sum > bestPhaseScore)
            {
                bestPhaseScore = sum;
                bestPhase = phase;
            }
        }

        var times = new List<double>();
        for (var i = bestPhase; i < onset.Length; i += bestLag) times.Add(Math.Round(i / FramesPerSecond, 3));
        return new Beats(times.ToArray(), bestLag / FramesPerSecond);
    }

    /// <summary>Half-wave rectified rise of log energy, 10 ms frames.</summary>
    internal static double[] OnsetStrength(float[] samples)
    {
        var frames = samples.Length / Hop;
        if (frames < 2) return Array.Empty<double>();
        var energy = new double[frames];
        for (var f = 0; f < frames; f++)
        {
            double sum = 0;
            var from = f * Hop;
            var to = Math.Min(samples.Length, from + Hop * 2);
            for (var i = from; i < to; i++) sum += samples[i] * samples[i];
            energy[f] = Math.Log(1e-6 + sum);
        }

        var onset = new double[frames];
        for (var f = 1; f < frames; f++) onset[f] = Math.Max(0, energy[f] - energy[f - 1]);

        // Remove the slow trend so loud passages don't drown out the pulse.
        var mean = onset.Average();
        for (var f = 0; f < frames; f++) onset[f] = Math.Max(0, onset[f] - mean);
        return onset;
    }
}
