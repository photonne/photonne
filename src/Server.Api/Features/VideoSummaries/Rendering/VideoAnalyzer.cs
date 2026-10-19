using System.Globalization;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>
/// A video measured second by second (index <c>i</c> covers <c>[i, i+1)</c>).
/// The planner scores these to find the moments worth showing.
/// </summary>
/// <param name="Motion">Mean luma change between frames (0-255).</param>
/// <param name="Brightness">Mean luma (0-255).</param>
/// <param name="Blur">ffmpeg's blur estimate: higher is blurrier.</param>
/// <param name="Loudness">Loudest momentary loudness (LUFS); null without audio.</param>
/// <param name="SceneCuts">Seconds where the shot changes.</param>
public sealed record VideoSignals(
    double DurationSeconds,
    float[] Motion,
    float[] Brightness,
    float[] Blur,
    float[]? Loudness,
    float[] SceneCuts)
{
    public int Seconds => Motion.Length;
}

/// <summary>
/// Measures a video in one ffmpeg pass at low resolution (160 px wide, 5 fps):
/// motion and brightness (<c>signalstats</c>), shot changes (<c>scdet</c>),
/// sharpness (<c>blurdetect</c>) and momentary loudness (<c>ebur128</c>).
/// Small enough to be quick — a few percent of the video's own length.
/// </summary>
public static class VideoAnalyzer
{
    /// <summary>Bump when what is measured, or how, changes: cached analyses
    /// with another version are measured again.</summary>
    public const int Version = 1;

    /// <summary><c>scdet</c> score (0-100) from which a frame starts a new shot.</summary>
    private const double SceneThreshold = 10;

    /// <summary>Silence floor for loudness, LUFS; ebur128 reports -120 for digital silence.</summary>
    private const float QuietFloor = -70;

    public static async Task<VideoSignals?> AnalyzeAsync(
        string path, MediaProbe probe, string workDirectory, CancellationToken ct)
    {
        if (!probe.HasVideo || probe.DurationSeconds <= 0) return null;

        Directory.CreateDirectory(workDirectory);
        var id = Guid.NewGuid().ToString("N");
        var videoLog = Path.Combine(workDirectory, $"{id}-video.txt");
        var audioLog = Path.Combine(workDirectory, $"{id}-audio.txt");
        try
        {
            // 8-bit before measuring: a 10-bit (HDR) source would report luma on a 0-1023 scale.
            var graph = "[0:v]fps=5,scale=160:-2,format=yuv420p,signalstats,"
                + $"scdet=threshold={SceneThreshold.ToString(CultureInfo.InvariantCulture)},"
                + "blurdetect=block_width=32:block_height=32,"
                + $"metadata=mode=print:file={FilterPath(videoLog)}[v]";
            var arguments = new List<string> { "-i", path };
            if (probe.HasAudio)
                graph += $";[0:a:0]ebur128=metadata=1,ametadata=mode=print:file={FilterPath(audioLog)}[a]";
            arguments.AddRange(new[] { "-filter_complex", graph, "-map", "[v]" });
            if (probe.HasAudio) arguments.AddRange(new[] { "-map", "[a]" });
            arguments.AddRange(new[] { "-f", "null", "-" });

            var error = await FfmpegProcess.RunFfmpegAsync(arguments, ct);
            if (error != null || !File.Exists(videoLog)) return null;

            var video = ParseFrames(await File.ReadAllTextAsync(videoLog, ct));
            var audio = probe.HasAudio && File.Exists(audioLog)
                ? ParseFrames(await File.ReadAllTextAsync(audioLog, ct))
                : null;
            return Aggregate(probe.DurationSeconds, video, audio);
        }
        finally
        {
            TryDelete(videoLog);
            TryDelete(audioLog);
        }
    }

    /// <summary>One frame of ffmpeg's <c>metadata=mode=print</c> output.</summary>
    internal sealed record MeasuredFrame(double Time, IReadOnlyDictionary<string, double> Values);

    /// <summary>Reads ffmpeg's <c>metadata=mode=print</c> output: a
    /// <c>frame:N pts:P pts_time:T</c> line, then <c>key=value</c> lines.</summary>
    internal static List<MeasuredFrame> ParseFrames(string text)
    {
        var frames = new List<MeasuredFrame>();
        Dictionary<string, double>? values = null;
        double time = 0;
        foreach (var raw in text.Split('\n'))
        {
            var line = raw.Trim();
            if (line.StartsWith("frame:", StringComparison.Ordinal))
            {
                if (values != null) frames.Add(new MeasuredFrame(time, values));
                values = new Dictionary<string, double>();
                var at = line.IndexOf("pts_time:", StringComparison.Ordinal);
                time = at >= 0 && double.TryParse(line[(at + 9)..].Trim(), NumberStyles.Float, CultureInfo.InvariantCulture, out var t)
                    ? t
                    : time;
                continue;
            }
            var equals = line.IndexOf('=');
            if (values == null || equals <= 0) continue;
            if (double.TryParse(line[(equals + 1)..], NumberStyles.Float, CultureInfo.InvariantCulture, out var value)
                && double.IsFinite(value))
            {
                values[line[..equals]] = value;
            }
        }
        if (values != null) frames.Add(new MeasuredFrame(time, values));
        return frames;
    }

    /// <summary>Folds frame measurements into one value per second.</summary>
    internal static VideoSignals Aggregate(double duration, List<MeasuredFrame> video, List<MeasuredFrame>? audio)
    {
        var seconds = Math.Max(1, (int)Math.Ceiling(duration - 1e-6));
        var motion = new float[seconds];
        var brightness = new float[seconds];
        var blur = new float[seconds];
        var counts = new int[seconds];
        var motionCounts = new int[seconds];
        var cuts = new List<float>();

        for (var f = 0; f < video.Count; f++)
        {
            var frame = video[f];
            var i = Math.Clamp((int)Math.Floor(frame.Time), 0, seconds - 1);
            counts[i]++;
            brightness[i] += (float)frame.Values.GetValueOrDefault("lavfi.signalstats.YAVG");
            blur[i] += (float)frame.Values.GetValueOrDefault("lavfi.blur");
            // The first frame has nothing to differ from.
            if (f > 0 && frame.Values.TryGetValue("lavfi.signalstats.YDIF", out var ydif))
            {
                motion[i] += (float)ydif;
                motionCounts[i]++;
            }
            if (f > 0 && frame.Values.GetValueOrDefault("lavfi.scd.score") >= SceneThreshold)
                cuts.Add((float)Math.Round(frame.Time, 2));
        }

        for (var i = 0; i < seconds; i++)
        {
            if (counts[i] > 0)
            {
                brightness[i] /= counts[i];
                blur[i] /= counts[i];
            }
            if (motionCounts[i] > 0) motion[i] /= motionCounts[i];
        }
        FillGaps(brightness, counts);
        FillGaps(blur, counts);
        FillGaps(motion, motionCounts);

        float[]? loudness = null;
        if (audio is { Count: > 0 })
        {
            loudness = Enumerable.Repeat(QuietFloor, seconds).ToArray();
            foreach (var frame in audio)
            {
                if (!frame.Values.TryGetValue("lavfi.r128.M", out var m)) continue;
                var i = Math.Clamp((int)Math.Floor(frame.Time), 0, seconds - 1);
                loudness[i] = Math.Max(loudness[i], Math.Max(QuietFloor, (float)m));
            }
        }

        return new VideoSignals(duration, motion, brightness, blur, loudness, cuts.ToArray());
    }

    /// <summary>A second without frames (a variable frame rate gap, the
    /// rounding of the last one) takes its neighbour's value.</summary>
    private static void FillGaps(float[] values, int[] counts)
    {
        for (var i = 0; i < values.Length; i++)
        {
            if (counts[i] > 0) continue;
            var previous = i - 1;
            while (previous >= 0 && counts[previous] == 0) previous--;
            var next = i + 1;
            while (next < values.Length && counts[next] == 0) next++;
            values[i] = previous >= 0 ? values[previous] : next < values.Length ? values[next] : 0;
        }
    }

    /// <summary>A path as a filter option value: forward slashes, and the
    /// characters filter syntax gives meaning to escaped.</summary>
    private static string FilterPath(string path)
    {
        // Inside quotes only ':' needs escaping; a quote closes, escapes and reopens.
        var escaped = path.Replace('\\', '/')
            .Replace(":", "\\:")
            .Replace("'", "'\\''");
        return $"'{escaped}'";
    }

    private static void TryDelete(string path)
    {
        try { File.Delete(path); } catch (IOException) { } catch (UnauthorizedAccessException) { }
    }
}
