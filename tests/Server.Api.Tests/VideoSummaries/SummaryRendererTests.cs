using System.Diagnostics;
using System.Text.Json;
using Microsoft.Extensions.Logging.Abstractions;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// Renders real clips with ffmpeg (the CI test job installs it, like the
/// server image ships it): mixed orientation, frame rates and audio come out
/// as one 1080p file with sound, the capture date in its header, and a clip
/// that can't be read is skipped instead of failing the whole summary.
/// </summary>
public sealed class SummaryRendererTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "photonne-renderer-tests", Guid.NewGuid().ToString("N"));

    public SummaryRendererTests() => Directory.CreateDirectory(_dir);

    public void Dispose()
    {
        try { Directory.Delete(_dir, recursive: true); } catch (IOException) { }
    }

    [Fact]
    public async Task MixedClips_BecomeOne1080pVideoWithSound()
    {
        var landscape = Path.Combine(_dir, "landscape.mp4");
        var portrait = Path.Combine(_dir, "portrait.mp4");
        var photo = Path.Combine(_dir, "photo.jpg");
        await Ffmpeg("-f", "lavfi", "-i", "testsrc=size=1280x720:rate=30:duration=4",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=4",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", landscape);
        // Portrait, 60 fps and no audio track at all.
        await Ffmpeg("-f", "lavfi", "-i", "testsrc2=size=720x1280:rate=60:duration=3",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", portrait);
        await Ffmpeg("-f", "lavfi", "-i", "testsrc=size=800x600", "-frames:v", "1", photo);

        var output = Path.Combine(_dir, "summary.mp4");
        var progress = new List<double>();
        var renderer = new SummaryRenderer(NullLogger<SummaryRenderer>.Instance);
        var captured = new DateTime(2024, 12, 31, 18, 30, 0, DateTimeKind.Utc);

        var count = await renderer.RenderAsync(
            new[]
            {
                new RenderClip(landscape, true, 1, 2),
                new RenderClip(Path.Combine(_dir, "missing.mp4"), true, 0, 2),
                new RenderClip(portrait, true, 0, 2),
                new RenderClip(photo, false, 0, 2),
            },
            new Frame(1920, 1080), output, "Resumen 2024", captured, Path.Combine(_dir, "work"),
            p => { progress.Add(p); return Task.CompletedTask; },
            CancellationToken.None);

        Assert.Equal(3, count);
        Assert.Equal(1, progress.Last());

        var probe = await FfmpegProcess.ProbeAsync(output, CancellationToken.None);
        Assert.NotNull(probe);
        Assert.True(probe!.HasVideo);
        Assert.True(probe.HasAudio);
        Assert.InRange(probe.DurationSeconds, 5.5, 6.6);

        using var info = JsonDocument.Parse(await Run("ffprobe", "-v", "error",
            "-show_entries", "stream=width,height:format_tags=creation_time,title", "-of", "json", output));
        var video = info.RootElement.GetProperty("streams").EnumerateArray()
            .First(s => s.TryGetProperty("width", out _));
        Assert.Equal(1920, video.GetProperty("width").GetInt32());
        Assert.Equal(1080, video.GetProperty("height").GetInt32());
        var tags = info.RootElement.GetProperty("format").GetProperty("tags");
        Assert.StartsWith("2024-12-31T18:30:00", tags.GetProperty("creation_time").GetString());
        Assert.Equal("Resumen 2024", tags.GetProperty("title").GetString());
    }

    [Fact]
    public async Task PortraitFrame_ComesOutUpright()
    {
        var portrait = Path.Combine(_dir, "portrait.mp4");
        var photo = Path.Combine(_dir, "tall.jpg");
        await Ffmpeg("-f", "lavfi", "-i", "testsrc2=size=720x1280:rate=30:duration=3",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", portrait);
        await Ffmpeg("-f", "lavfi", "-i", "testsrc=size=600x800", "-frames:v", "1", photo);

        var output = Path.Combine(_dir, "vertical.mp4");
        var count = await new SummaryRenderer(NullLogger<SummaryRenderer>.Instance).RenderAsync(
            new[] { new RenderClip(portrait, true, 0, 2), new RenderClip(photo, false, 0, 2) },
            new Frame(1080, 1920), output, "Resumen", DateTime.UtcNow, Path.Combine(_dir, "work"),
            _ => Task.CompletedTask, CancellationToken.None);

        Assert.Equal(2, count);
        var probe = await FfmpegProcess.ProbeAsync(output, CancellationToken.None);
        Assert.Equal(1080, probe!.DisplayWidth);
        Assert.Equal(1920, probe.DisplayHeight);
    }

    [Fact]
    public async Task Probe_ReadsTheShapeAsPlayed_ThroughThePhonesRotationFlag()
    {
        // Phones store portrait video landscape and flag it to be turned.
        var stored = Path.Combine(_dir, "stored.mp4");
        var flagged = Path.Combine(_dir, "flagged.mp4");
        await Ffmpeg("-f", "lavfi", "-i", "testsrc=size=1280x720:rate=30:duration=1",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", stored);
        await Ffmpeg("-display_rotation", "90", "-i", stored, "-c", "copy", flagged);

        var plain = await FfmpegProcess.ProbeAsync(stored, CancellationToken.None);
        var turned = await FfmpegProcess.ProbeAsync(flagged, CancellationToken.None);

        Assert.True(plain!.AspectRatio > 1);
        Assert.Equal(720, turned!.DisplayWidth);
        Assert.Equal(1280, turned.DisplayHeight);
    }

    [Fact]
    public async Task Finished_WithTitle_Captions_Crossfades_Chapters_AndMusic()
    {
        var a = Path.Combine(_dir, "a.mp4");
        var b = Path.Combine(_dir, "b.mp4");
        var photo = Path.Combine(_dir, "p.jpg");
        var music = Path.Combine(_dir, "music.wav");
        await Ffmpeg("-f", "lavfi", "-i", "testsrc=size=1280x720:rate=30:duration=4",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=4",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", a);
        await Ffmpeg("-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=4",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", b);
        await Ffmpeg("-f", "lavfi", "-i", "testsrc=size=800x600", "-frames:v", "1", photo);
        // A short loop of clicks: has to be repeated to cover the summary.
        await Ffmpeg("-f", "lavfi", "-i", "aevalsrc='if(lt(mod(t,0.5),0.03),sin(2*PI*1000*t),0)':s=44100:d=4", music);

        var font = FontLocator.Find();
        var style = new RenderStyle
        {
            Crossfade = 0.5,
            Title = "Resumen 2024",
            Subtitle = "Enero – diciembre de 2024",
            FontFile = font,
            MusicPath = music,
            ChunkSize = 2,
        };
        var output = Path.Combine(_dir, "finished.mp4");

        var count = await new SummaryRenderer(NullLogger<SummaryRenderer>.Instance).RenderAsync(
            new[]
            {
                new RenderClip(a, true, 0, 3, Caption: "Julio · Barcelona"),
                new RenderClip(b, true, 0, 3),
                new RenderClip(photo, false, 0, 3, Caption: "Agosto"),
            },
            new Frame(1920, 1080), output, "Resumen 2024", DateTime.UtcNow, Path.Combine(_dir, "work"),
            _ => Task.CompletedTask, CancellationToken.None, style);

        Assert.Equal(3, count);
        var probe = (await FfmpegProcess.ProbeAsync(output, CancellationToken.None))!;
        Assert.True(probe.HasAudio);
        // Title card first when there's a font; within each chapter of two
        // segments the crossfade overlaps them by half a second.
        var segments = font != null ? new[] { 3.5, 3, 3, 3 } : new[] { 3.0, 3, 3 };
        var expected = segments.Chunk(2).Sum(chapter => chapter.Sum() - 0.5 * (chapter.Length - 1));
        Assert.InRange(probe.DurationSeconds, expected - 0.3, expected + 0.3);
    }

    [Fact]
    public async Task NothingRenderable_Fails()
    {
        var renderer = new SummaryRenderer(NullLogger<SummaryRenderer>.Instance);

        await Assert.ThrowsAsync<InvalidOperationException>(() => renderer.RenderAsync(
            new[] { new RenderClip(Path.Combine(_dir, "missing.mp4"), true, 0, 2) },
            new Frame(1920, 1080), Path.Combine(_dir, "out.mp4"), "x", DateTime.UtcNow, Path.Combine(_dir, "work"),
            _ => Task.CompletedTask, CancellationToken.None));
    }

    private static Task Ffmpeg(params string[] args) =>
        Run("ffmpeg", new[] { "-hide_banner", "-loglevel", "error", "-y" }.Concat(args).ToArray());

    private static async Task<string> Run(string exe, params string[] args)
    {
        var psi = new ProcessStartInfo(exe)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
        };
        foreach (var a in args) psi.ArgumentList.Add(a);
        using var process = Process.Start(psi)!;
        var stdout = process.StandardOutput.ReadToEndAsync();
        var stderr = process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        Assert.True(process.ExitCode == 0, $"{exe} failed: {await stderr}");
        return await stdout;
    }
}
