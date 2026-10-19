using System.Globalization;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// Measures a video second by second with two cheap ffmpeg passes:
///   * picture: 5 frames a second at 160 px wide — motion (difference with the
///     previous frame), brightness, blur and shot changes;
///   * sound: momentary loudness (EBU R128) every 100 ms.
/// Decoding is most of the cost, so reference-less frames are skipped
/// (<c>-skip_frame noref</c>) and very long videos are only read up to
/// <see cref="MaxSeconds"/>: a 4K clip analyses several times faster than
/// real time.
/// </summary>
public static class VideoAnalyzer
{
    /// <summary>Bump when what's measured changes, so cached profiles are redone.</summary>
    public const int Version = 1;

    private const int MaxSeconds = 20 * 60;
    private const double SceneCutScore = 0.3;

    /// <summary>The video's profile, or null when ffmpeg can't read its picture.</summary>
    public static async Task<VideoProfile?> AnalyzeAsync(string path, string workDirectory, CancellationToken ct)
    {
        var probe = await FfmpegProcess.ProbeAsync(path, ct);
        if (probe is not { HasVideo: true } || probe.DurationSeconds <= 0) return null;

        Directory.CreateDirectory(workDirectory);
        var id = Guid.NewGuid().ToString("N");
        var pictureFile = Path.Combine(workDirectory, $"picture-{id}.txt");
        var soundFile = Path.Combine(workDirectory, $"sound-{id}.txt");
        try
        {
            var duration = Math.Min(probe.DurationSeconds, MaxSeconds);
            var limit = duration.ToString("0.###", CultureInfo.InvariantCulture);

            var pictureError = await FfmpegProcess.RunFfmpegAsync(new[]
            {
                "-skip_frame", "noref", "-t", limit, "-i", path, "-an",
                "-vf", "fps=5,scale=160:-2,select='gte(scene,0)',signalstats,blurdetect," +
                       $"metadata=mode=print:file={FilterPath(pictureFile)}",
                "-f", "null", "-",
            }, ct);
            if (pictureError != null || !File.Exists(pictureFile)) return null;

            if (probe.HasAudio)
            {
                // Without sound the profile just has no loudness; not a failure.
                await FfmpegProcess.RunFfmpegAsync(new[]
                {
                    "-t", limit, "-i", path, "-vn",
                    "-af", "aresample=16000,ebur128=metadata=1," +
                           $"ametadata=mode=print:key=lavfi.r128.M:file={FilterPath(soundFile)}",
                    "-f", "null", "-",
                }, ct);
            }

            return Build(
                duration,
                ParseMetadata(await File.ReadAllLinesAsync(pictureFile, ct)),
                File.Exists(soundFile) ? ParseMetadata(await File.ReadAllLinesAsync(soundFile, ct)) : new());
        }
        finally
        {
            File.Delete(pictureFile);
            File.Delete(soundFile);
        }
    }

    /// <summary>Folds per-frame readings into per-second ones.</summary>
    internal static VideoProfile Build(
        double duration,
        List<(double Time, Dictionary<string, double> Values)> picture,
        List<(double Time, Dictionary<string, double> Values)> sound)
    {
        var seconds = Math.Max(1, (int)Math.Ceiling(duration));
        double?[] Mean(IEnumerable<(double Time, Dictionary<string, double> Values)> frames, string key, Func<double, bool>? valid = null)
        {
            var sums = new double[seconds];
            var counts = new int[seconds];
            foreach (var (time, values) in frames)
            {
                if (!values.TryGetValue(key, out var value) || double.IsNaN(value) || double.IsInfinity(value)) continue;
                if (valid != null && !valid(value)) continue;
                var second = Math.Clamp((int)Math.Floor(time), 0, seconds - 1);
                sums[second] += value;
                counts[second]++;
            }
            return Enumerable.Range(0, seconds)
                .Select(s => counts[s] > 0 ? (double?)(sums[s] / counts[s]) : null)
                .ToArray();
        }

        var sceneCuts = picture
            .Where(f => f.Time > 0.5 && f.Values.TryGetValue("lavfi.scene_score", out var score) && score >= SceneCutScore)
            .Select(f => Math.Round(f.Time, 2))
            .ToArray();

        return new VideoProfile(
            Math.Round(duration, 3),
            Mean(picture, "lavfi.signalstats.YDIF"),
            Mean(picture, "lavfi.signalstats.YAVG"),
            Mean(picture, "lavfi.blur"),
            // ebur128 reports −120 and lower before it has heard anything.
            Mean(sound, "lavfi.r128.M", value => value > -100),
            sceneCuts);
    }

    /// <summary>Parses ffmpeg's <c>metadata=print</c> output: a
    /// <c>frame:N pts:P pts_time:T</c> line followed by <c>key=value</c> lines.</summary>
    internal static List<(double Time, Dictionary<string, double> Values)> ParseMetadata(IEnumerable<string> lines)
    {
        var frames = new List<(double, Dictionary<string, double>)>();
        Dictionary<string, double>? current = null;
        foreach (var line in lines)
        {
            if (line.StartsWith("frame:", StringComparison.Ordinal))
            {
                var at = line.IndexOf("pts_time:", StringComparison.Ordinal);
                if (at < 0
                    || !double.TryParse(line[(at + "pts_time:".Length)..].Trim(), NumberStyles.Float, CultureInfo.InvariantCulture, out var time))
                {
                    current = null;
                    continue;
                }
                current = new Dictionary<string, double>();
                frames.Add((time, current));
                continue;
            }

            var eq = line.IndexOf('=');
            if (current == null || eq <= 0) continue;
            var raw = line[(eq + 1)..].Trim();
            current[line[..eq]] = double.TryParse(raw, NumberStyles.Float, CultureInfo.InvariantCulture, out var value)
                ? value
                : double.NaN; // "-nan" on a flat frame
        }
        return frames;
    }

    /// <summary>A path as a filter option value: ':' and '\' separate options
    /// and escape characters inside a filter graph, so they're escaped.</summary>
    private static string FilterPath(string path) =>
        path.Replace("\\", "/").Replace(":", "\\:").Replace("'", "\\'");
}
