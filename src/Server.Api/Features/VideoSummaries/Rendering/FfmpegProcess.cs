using System.Diagnostics;
using System.Globalization;
using System.Text.Json;
using Xabe.FFmpeg;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>What the renderer needs to know about a source video.</summary>
/// <param name="IsHdr">PQ or HLG transfer — needs tone mapping or it comes out grey.</param>
/// <param name="DisplayWidth">Width as played, after the rotation phones record
/// portrait video with (stored landscape, flagged to be turned). 0 when unknown.</param>
public sealed record MediaProbe(
    double DurationSeconds, bool HasVideo, bool HasAudio, bool IsHdr, int DisplayWidth = 0, int DisplayHeight = 0)
{
    /// <summary>Width over height as played; null when unknown.</summary>
    public double? AspectRatio => DisplayWidth > 0 && DisplayHeight > 0 ? (double)DisplayWidth / DisplayHeight : null;
}

/// <summary>
/// Runs ffmpeg/ffprobe as child processes. Arguments go through
/// <see cref="ProcessStartInfo.ArgumentList"/>, never a joined string, so a
/// user's file name can't break (or inject into) the command line. Rendering
/// is long and optional, so it runs below normal priority — browsing the
/// library while a summary renders must not stutter — and a cancelled render
/// kills its ffmpeg instead of leaving it running behind us.
/// </summary>
public static class FfmpegProcess
{
    private static string Executable(string name)
    {
        var exe = OperatingSystem.IsWindows() ? name + ".exe" : name;
        return string.IsNullOrEmpty(FFmpeg.ExecutablesPath) ? exe : Path.Combine(FFmpeg.ExecutablesPath, exe);
    }

    /// <summary>Runs ffmpeg; returns null on success, its error output otherwise.</summary>
    public static async Task<string?> RunFfmpegAsync(IEnumerable<string> arguments, CancellationToken ct)
    {
        var (exitCode, _, stderr) = await RunAsync(
            Executable("ffmpeg"),
            new[] { "-hide_banner", "-nostdin", "-loglevel", "error", "-y" }.Concat(arguments),
            ct);
        return exitCode == 0 ? null : string.IsNullOrWhiteSpace(stderr) ? $"ffmpeg exited {exitCode}" : stderr.Trim();
    }

    /// <summary>Duration and streams of a media file, or null when ffprobe can't read it.</summary>
    public static async Task<MediaProbe?> ProbeAsync(string path, CancellationToken ct)
    {
        var (exitCode, stdout, _) = await RunAsync(
            Executable("ffprobe"),
            new[]
            {
                "-v", "error",
                "-show_entries", "format=duration:stream=codec_type,color_transfer,duration,width,height:stream_tags=rotate:stream_side_data=rotation",
                "-of", "json",
                path,
            },
            ct);
        if (exitCode != 0 || string.IsNullOrWhiteSpace(stdout)) return null;

        try
        {
            using var doc = JsonDocument.Parse(stdout);
            var root = doc.RootElement;

            double duration = 0;
            if (root.TryGetProperty("format", out var format)
                && format.TryGetProperty("duration", out var d))
            {
                duration = ParseSeconds(d.GetString());
            }

            bool hasVideo = false, hasAudio = false, isHdr = false;
            int width = 0, height = 0;
            if (root.TryGetProperty("streams", out var streams))
            {
                foreach (var stream in streams.EnumerateArray())
                {
                    var type = stream.TryGetProperty("codec_type", out var t) ? t.GetString() : null;
                    if (type == "audio") hasAudio = true;
                    if (type != "video") continue;

                    if (hasVideo) continue; // the first video stream is the picture; others are covers
                    hasVideo = true;
                    width = stream.TryGetProperty("width", out var w) && w.ValueKind == JsonValueKind.Number ? w.GetInt32() : 0;
                    height = stream.TryGetProperty("height", out var h) && h.ValueKind == JsonValueKind.Number ? h.GetInt32() : 0;
                    if (Math.Abs(Rotation(stream)) % 180 == 90) (width, height) = (height, width);
                    var transfer = stream.TryGetProperty("color_transfer", out var c) ? c.GetString() : null;
                    if (transfer is "smpte2084" or "arib-std-b67") isHdr = true;
                    if (duration <= 0 && stream.TryGetProperty("duration", out var sd))
                        duration = ParseSeconds(sd.GetString());
                }
            }

            return new MediaProbe(duration, hasVideo, hasAudio, isHdr, width, height);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    /// <summary>Degrees the stream is turned on playback: the display matrix
    /// side data on current ffmpeg, the legacy "rotate" tag on older files.</summary>
    private static int Rotation(JsonElement stream)
    {
        if (stream.TryGetProperty("side_data_list", out var sideData))
        {
            foreach (var item in sideData.EnumerateArray())
            {
                if (item.TryGetProperty("rotation", out var r) && r.ValueKind == JsonValueKind.Number)
                    return (int)Math.Round(r.GetDouble());
            }
        }
        if (stream.TryGetProperty("tags", out var tags) && tags.TryGetProperty("rotate", out var tag)
            && int.TryParse(tag.GetString(), out var degrees))
            return degrees;
        return 0;
    }

    private static double ParseSeconds(string? value) =>
        double.TryParse(value, NumberStyles.Float, CultureInfo.InvariantCulture, out var s) && s > 0 ? s : 0;

    private static async Task<(int ExitCode, string Stdout, string Stderr)> RunAsync(
        string fileName, IEnumerable<string> arguments, CancellationToken ct)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = fileName,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        foreach (var argument in arguments) startInfo.ArgumentList.Add(argument);

        using var process = new Process { StartInfo = startInfo };
        process.Start();
        try
        {
            process.PriorityClass = ProcessPriorityClass.BelowNormal;
        }
        catch (Exception)
        {
            // Not permitted on every host; rendering at normal priority is fine.
        }

        // Drain both pipes so a chatty ffmpeg can't deadlock on a full buffer.
        var stdout = process.StandardOutput.ReadToEndAsync(CancellationToken.None);
        var stderr = process.StandardError.ReadToEndAsync(CancellationToken.None);
        try
        {
            await process.WaitForExitAsync(ct);
        }
        catch (OperationCanceledException)
        {
            try { process.Kill(entireProcessTree: true); } catch (Exception) { /* already gone */ }
            throw;
        }

        return (process.ExitCode, await stdout, await stderr);
    }
}
