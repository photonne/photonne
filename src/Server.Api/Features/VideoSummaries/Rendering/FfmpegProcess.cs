using System.Diagnostics;
using System.Globalization;
using System.Text.Json;
using Xabe.FFmpeg;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>What the renderer needs to know about a source video.</summary>
/// <param name="IsHdr">PQ or HLG transfer — needs tone mapping or it comes out grey.</param>
public sealed record MediaProbe(double DurationSeconds, bool HasVideo, bool HasAudio, bool IsHdr);

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
                "-show_entries", "format=duration:stream=codec_type,color_transfer,duration",
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
            if (root.TryGetProperty("streams", out var streams))
            {
                foreach (var stream in streams.EnumerateArray())
                {
                    var type = stream.TryGetProperty("codec_type", out var t) ? t.GetString() : null;
                    if (type == "audio") hasAudio = true;
                    if (type != "video") continue;

                    hasVideo = true;
                    var transfer = stream.TryGetProperty("color_transfer", out var c) ? c.GetString() : null;
                    if (transfer is "smpte2084" or "arib-std-b67") isHdr = true;
                    if (duration <= 0 && stream.TryGetProperty("duration", out var sd))
                        duration = ParseSeconds(sd.GetString());
                }
            }

            return new MediaProbe(duration, hasVideo, hasAudio, isHdr);
        }
        catch (JsonException)
        {
            return null;
        }
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
