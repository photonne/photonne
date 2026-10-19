using System.Diagnostics;
using Photonne.Server.Api.Features.VideoSummaries.Highlights;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// The analyzer on a real clip: 4 s of black silence, 4 s of a moving picture
/// with a tone, 4 s of a still grey frame with faint noise. Each part reads as
/// what it is, the shot changes are found, and the finder picks the middle.
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
    public async Task ReadsEachPart_AndThePickLandsOnTheLivelyOne()
    {
        var clip = Path.Combine(_dir, "parts.mp4");
        await Ffmpeg(
            "-f", "lavfi", "-i", "color=black:s=640x360:r=30:d=4",
            "-f", "lavfi", "-i", "testsrc2=s=640x360:r=30:d=4",
            "-f", "lavfi", "-i", "color=gray:s=640x360:r=30:d=4",
            "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo:d=4",
            "-f", "lavfi", "-i", "sine=f=440:d=4:sample_rate=48000",
            "-f", "lavfi", "-i", "anoisesrc=d=4:a=0.02:r=48000",
            "-filter_complex", "[0:v][3:a][1:v][4:a][2:v][5:a]concat=n=3:v=1:a=1[v][a]",
            "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", clip);

        var profile = await VideoAnalyzer.AnalyzeAsync(clip, Path.Combine(_dir, "work"), CancellationToken.None);

        Assert.NotNull(profile);
        Assert.InRange(profile!.Seconds, 12, 13);
        Assert.True(profile.Brightness[1] < 25, "black part reads dark");
        Assert.True(profile.Motion[6] > 0.5, "moving part reads moving");
        Assert.True(profile.Motion[10] < 0.2, "still part reads frozen");
        Assert.True(profile.Loudness[6] > profile.Loudness[10] + 10, "the tone is louder than the noise");
        Assert.Null(profile.Loudness[1]); // silence has no reading
        Assert.Contains(profile.SceneCuts, t => Math.Abs(t - 4) < 0.3);
        Assert.Contains(profile.SceneCuts, t => Math.Abs(t - 8) < 0.3);

        var pick = Assert.Single(HighlightFinder.Pick(profile, 4));
        Assert.Equal(4, pick.Start, 1);
        Assert.Equal(8, pick.End, 1);
    }

    private static async Task Ffmpeg(params string[] args)
    {
        var psi = new ProcessStartInfo("ffmpeg") { RedirectStandardError = true, UseShellExecute = false };
        foreach (var a in new[] { "-hide_banner", "-loglevel", "error", "-y" }.Concat(args)) psi.ArgumentList.Add(a);
        using var process = Process.Start(psi)!;
        var stderr = await process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        Assert.True(process.ExitCode == 0, "ffmpeg failed: " + stderr);
    }
}
