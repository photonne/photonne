using System.Diagnostics;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// Measures real clips with ffmpeg (installed in CI and in the server image):
/// black, movement, sound and a change of shot come out where they are, one
/// value per second.
/// </summary>
public sealed class VideoAnalyzerTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "photonne-analyzer-tests", Guid.NewGuid().ToString("N"));

    public VideoAnalyzerTests() => Directory.CreateDirectory(_dir);

    public void Dispose()
    {
        try { Directory.Delete(_dir, recursive: true); } catch (IOException) { }
    }

    [Fact]
    public async Task MeasuresBlackMovementSoundAndShotChanges_PerSecond()
    {
        // 3 s of black and silence, then 3 s of a moving picture with a tone.
        var path = Path.Combine(_dir, "clip.mp4");
        await Ffmpeg(
            "-f", "lavfi", "-i", "color=c=black:size=640x360:rate=30:duration=3",
            "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30:duration=3",
            "-f", "lavfi", "-i", "anullsrc=r=48000:cl=mono:d=3",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
            "-filter_complex", "[0:v][2:a][1:v][3:a]concat=n=2:v=1:a=1[v][a]",
            "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", path);
        var probe = (await FfmpegProcess.ProbeAsync(path, CancellationToken.None))!;

        var signals = await VideoAnalyzer.AnalyzeAsync(path, probe, Path.Combine(_dir, "work"), CancellationToken.None);

        Assert.NotNull(signals);
        Assert.Equal(6, signals!.Seconds);
        Assert.All(signals.Brightness[..3], b => Assert.True(b < 20, $"black: {b}"));
        Assert.All(signals.Brightness[3..], b => Assert.True(b > 40, $"picture: {b}"));
        Assert.True(signals.Motion[4] > signals.Motion[1] + 1, "the picture moves");
        Assert.NotNull(signals.Loudness);
        Assert.True(signals.Loudness![1] < -60, "silence");
        Assert.True(signals.Loudness[5] > -30, "the tone");
        Assert.Contains(signals.SceneCuts, c => c is >= 2.8f and <= 3.3f);
        Assert.Empty(Directory.GetFiles(Path.Combine(_dir, "work")));
    }

    [Fact]
    public async Task AVideoWithoutSound_HasNoLoudness()
    {
        var path = Path.Combine(_dir, "silent.mp4");
        await Ffmpeg("-f", "lavfi", "-i", "testsrc=size=320x240:rate=30:duration=2",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", path);
        var probe = (await FfmpegProcess.ProbeAsync(path, CancellationToken.None))!;

        var signals = await VideoAnalyzer.AnalyzeAsync(path, probe, Path.Combine(_dir, "work"), CancellationToken.None);

        Assert.NotNull(signals);
        Assert.Null(signals!.Loudness);
        Assert.Equal(2, signals.Seconds);
    }

    [Fact]
    public void ParseFrames_ReadsFfmpegsMetadataPrintout()
    {
        const string printout = """
            frame:0    pts:0       pts_time:0
            lavfi.signalstats.YAVG=125.9
            lavfi.signalstats.YDIF=0
            frame:1    pts:1       pts_time:0.2
            lavfi.signalstats.YAVG=126.1
            lavfi.signalstats.YDIF=3.5
            lavfi.scd.score=nan
            """;

        var frames = VideoAnalyzer.ParseFrames(printout);

        Assert.Equal(2, frames.Count);
        Assert.Equal(0.2, frames[1].Time);
        Assert.Equal(3.5, frames[1].Values["lavfi.signalstats.YDIF"]);
        Assert.False(frames[1].Values.ContainsKey("lavfi.scd.score"));
    }

    private static async Task Ffmpeg(params string[] args)
    {
        var psi = new ProcessStartInfo("ffmpeg")
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
        };
        foreach (var a in new[] { "-hide_banner", "-loglevel", "error", "-y" }.Concat(args)) psi.ArgumentList.Add(a);
        using var process = Process.Start(psi)!;
        var stderr = process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        Assert.True(process.ExitCode == 0, $"ffmpeg failed: {await stderr}");
    }
}
