using System.Globalization;
using ImageMagick;
using Photonne.Server.Api.Shared.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Formats.Jpeg;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>The summary's frame, in pixels: 1920×1080 or, for a portrait summary, 1080×1920.</summary>
public readonly record struct Frame(int Width, int Height);

/// <summary>A planned clip, resolved to files on disk.</summary>
/// <param name="FallbackImagePath">For photos: a pre-oriented thumbnail to use
/// when the original can't be decoded.</param>
public sealed record RenderClip(
    string SourcePath, bool IsVideo, double Start, double Length, string? FallbackImagePath = null);

/// <summary>
/// Cuts planned clips out of their sources and joins them into one MP4.
///
/// Every clip is first rendered to a segment with exactly the same format —
/// the <see cref="Frame"/>, 30 fps, H.264 yuv420p, AAC stereo 48 kHz — so the segments can be
/// joined with the concat demuxer as a stream copy instead of a second encode.
/// Normalising per clip is also what makes a year of mixed material work:
/// portrait and landscape, 30 and 60 fps, HDR iPhone clips next to SDR ones.
///
/// * Anything not the frame's shape is fitted over a blurred, zoomed copy of itself
///   instead of black bars.
/// * HDR (PQ/HLG) is tone-mapped to SDR; played as-is it looks washed out.
/// * Audio is loudness-normalised per clip so a quiet clip after a loud one
///   doesn't make the viewer reach for the volume, and videos without audio
///   (and photos) get silence so every segment has the same streams.
/// * Photos get a slow Ken Burns zoom, alternating in and out.
/// * Each clip fades in and out; cheap, and it hides the hard cuts.
///
/// A clip that fails to render is skipped and logged — one corrupt video must
/// not cost the user the whole summary. Only when nothing renders does it fail.
/// </summary>
public sealed class SummaryRenderer
{
    private const int Fps = 30;
    private const double Fade = 0.3;

    private readonly ILogger<SummaryRenderer> _logger;

    public SummaryRenderer(ILogger<SummaryRenderer> logger)
    {
        _logger = logger;
    }

    /// <summary>Renders <paramref name="clips"/> in order into
    /// <paramref name="outputPath"/>. Returns how many clips made it in.</summary>
    /// <param name="creationTimeUtc">Written as the MP4's creation time, which
    /// is where the indexer reads a video's capture date from.</param>
    public async Task<int> RenderAsync(
        IReadOnlyList<RenderClip> clips,
        Frame f,
        string outputPath,
        string title,
        DateTime creationTimeUtc,
        string workDirectory,
        Func<double, Task> progress,
        CancellationToken ct)
    {
        Directory.CreateDirectory(workDirectory);
        var segments = new List<string>(clips.Count);

        for (var i = 0; i < clips.Count; i++)
        {
            ct.ThrowIfCancellationRequested();
            var clip = clips[i];
            var segment = Path.Combine(workDirectory, $"segment-{i:00000}.mp4");

            string? error;
            try
            {
                error = clip.IsVideo
                    ? await RenderVideoSegmentAsync(clip, segment, f, ct)
                    : await RenderPhotoSegmentAsync(clip, segment, i, workDirectory, f, ct);
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                error = ex.Message;
            }

            if (error == null && File.Exists(segment)) segments.Add(segment);
            else _logger.LogWarning("Summary clip skipped ({Source}): {Error}", clip.SourcePath, error);

            // Segments are the expensive part; the join is a stream copy.
            await progress(0.95 * (i + 1) / clips.Count);
        }

        if (segments.Count == 0)
            throw new InvalidOperationException("No se pudo procesar ningún vídeo ni foto.");

        var list = Path.Combine(workDirectory, "segments.txt");
        await File.WriteAllLinesAsync(list,
            segments.Select(s => $"file '{s.Replace("'", "'\\''")}'"), ct);

        var joinError = await FfmpegProcess.RunFfmpegAsync(new[]
        {
            "-f", "concat", "-safe", "0", "-i", list,
            "-map", "0", "-c", "copy",
            "-map_metadata", "-1",
            "-metadata", "title=" + title,
            "-metadata", "creation_time=" + creationTimeUtc.ToString("yyyy-MM-dd'T'HH:mm:ss'.000000Z'", CultureInfo.InvariantCulture),
            "-movflags", "+faststart",
            outputPath,
        }, ct);
        if (joinError != null)
            throw new InvalidOperationException("No se pudo unir el vídeo final: " + joinError);

        await progress(1);
        return segments.Count;
    }

    private static async Task<string?> RenderVideoSegmentAsync(RenderClip clip, string output, Frame f, CancellationToken ct)
    {
        var probe = await FfmpegProcess.ProbeAsync(clip.SourcePath, ct);
        if (probe is not { HasVideo: true }) return "no video stream";

        var error = await RunVideoSegmentAsync(clip, output, probe, f, toneMap: probe.IsHdr, ct);
        // zscale isn't in every ffmpeg build. Grey colours beat a missing clip.
        if (error != null && probe.IsHdr && error.Contains("zscale", StringComparison.OrdinalIgnoreCase))
            error = await RunVideoSegmentAsync(clip, output, probe, f, toneMap: false, ct);
        return error;
    }

    private static Task<string?> RunVideoSegmentAsync(
        RenderClip clip, string output, MediaProbe probe, Frame f, bool toneMap, CancellationToken ct)
    {
        var length = Seconds(clip.Length);
        var hdrToSdr = toneMap
            ? "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,"
            : "";
        var audioSource = probe.HasAudio ? "[0:a]" : "[1:a]";
        var graph =
            $"[0:v]{hdrToSdr}{FitFilter("v", f)},fps={Fps},format=yuv420p,{VideoFades(clip.Length)}[v];" +
            $"{audioSource}{AudioChain(clip.Length, normalise: probe.HasAudio)}[a]";

        return FfmpegProcess.RunFfmpegAsync(
            new[]
            {
                "-ss", Seconds(clip.Start), "-t", length, "-i", clip.SourcePath,
                "-f", "lavfi", "-t", length, "-i", "anullsrc=r=48000:cl=stereo",
                "-filter_complex", graph,
            }.Concat(SegmentOutput(length, output)),
            ct);
    }

    private static async Task<string?> RenderPhotoSegmentAsync(
        RenderClip clip, string output, int index, string workDirectory, Frame f, CancellationToken ct)
    {
        var frame = Path.Combine(workDirectory, $"frame-{index:00000}.jpg");
        if (!await ComposeFrameAsync(clip.SourcePath, frame, f, ct)
            && (clip.FallbackImagePath == null || !await ComposeFrameAsync(clip.FallbackImagePath, frame, f, ct)))
        {
            return "could not decode the photo";
        }

        var frames = (int)Math.Round(clip.Length * Fps);
        const double zoom = 0.10;
        // Alternate zooming in and out so a run of photos doesn't feel like one
        // long push forward.
        var z = index % 2 == 0
            ? $"1+{zoom.ToString(CultureInfo.InvariantCulture)}*on/{frames}"
            : $"1+{zoom.ToString(CultureInfo.InvariantCulture)}-{zoom.ToString(CultureInfo.InvariantCulture)}*on/{frames}";
        var length = Seconds(clip.Length);
        // Upscaled before zoompan: it moves in whole pixels, which judders at 1080p.
        var graph =
            $"[0:v]scale={f.Width * 2}:{f.Height * 2},zoompan=z='{z}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'" +
            $":d={frames}:s={f.Width}x{f.Height}:fps={Fps},setsar=1,format=yuv420p,{VideoFades(clip.Length)}[v];" +
            $"[1:a]{AudioChain(clip.Length, normalise: false)}[a]";

        try
        {
            return await FfmpegProcess.RunFfmpegAsync(
                new[]
                {
                    "-i", frame,
                    "-f", "lavfi", "-t", length, "-i", "anullsrc=r=48000:cl=stereo",
                    "-filter_complex", graph,
                }.Concat(SegmentOutput(length, output)),
                ct);
        }
        finally
        {
            File.Delete(frame);
        }
    }

    /// <summary>
    /// Draws the photo, upright, fitted over a blurred fill of itself on a
    /// frame-sized canvas. Done here rather than in ffmpeg because ffmpeg ignores
    /// EXIF orientation on stills and can't read HEIC or RAW; ImageSharp
    /// (or ImageMagick, for those) handles both, exactly as the thumbnails do.
    /// </summary>
    private static async Task<bool> ComposeFrameAsync(string source, string output, Frame f, CancellationToken ct)
    {
        try
        {
            using var photo = await LoadUprightAsync(source, f, ct);
            using var canvas = photo.Clone(x => x
                .Resize(new ResizeOptions { Size = new Size(f.Width / 8, f.Height / 8), Mode = ResizeMode.Crop })
                .GaussianBlur(6)
                .Resize(f.Width, f.Height)
                .Brightness(0.7f));
            photo.Mutate(x => x.Resize(new ResizeOptions { Size = new Size(f.Width, f.Height), Mode = ResizeMode.Max }));
            canvas.Mutate(x => x.DrawImage(photo, new Point((f.Width - photo.Width) / 2, (f.Height - photo.Height) / 2), 1f));
            await canvas.SaveAsJpegAsync(output, new JpegEncoder { Quality = 92 }, ct);
            return true;
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception)
        {
            return false;
        }
    }

    private static async Task<Image<Rgb24>> LoadUprightAsync(string source, Frame f, CancellationToken ct)
    {
        var extension = Path.GetExtension(source);
        if (MediaFileTypes.IsHeic(extension) || MediaFileTypes.IsRaw(extension))
        {
            using var magick = RawImageLoader.Load(source);
            magick.AutoOrient();
            magick.Resize(new MagickGeometry((uint)f.Width, (uint)f.Height));
            magick.Format = MagickFormat.Jpeg;
            return Image.Load<Rgb24>(magick.ToByteArray());
        }

        var image = await Image.LoadAsync<Rgb24>(source, ct);
        image.Mutate(x => x.AutoOrient());
        return image;
    }

    /// <summary>Fits <c>[in]</c> into the frame over a blurred fill of itself.
    /// The fill is blurred at a quarter of the size: same look, a fraction of the cost.</summary>
    private static string FitFilter(string label, Frame f) =>
        $"setsar=1,split=2[{label}bg][{label}fg];" +
        $"[{label}bg]scale={f.Width / 4}:{f.Height / 4}:force_original_aspect_ratio=increase,crop={f.Width / 4}:{f.Height / 4}," +
        $"boxblur=10:2,eq=brightness=-0.08,scale={f.Width}:{f.Height},setsar=1[{label}bgb];" +
        $"[{label}fg]scale={f.Width}:{f.Height}:force_original_aspect_ratio=decrease,setsar=1[{label}fgs];" +
        $"[{label}bgb][{label}fgs]overlay=(W-w)/2:(H-h)/2";

    private static string VideoFades(double length) =>
        $"fade=t=in:st=0:d={Seconds(Fade)},fade=t=out:st={Seconds(Math.Max(0, length - Fade))}:d={Seconds(Fade)}";

    private static string AudioChain(double length, bool normalise) =>
        (normalise ? "loudnorm=I=-18:TP=-2:LRA=11," : "") +
        $"aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo,apad,atrim=0:{Seconds(length)}," +
        $"afade=t=in:st=0:d={Seconds(Fade)},afade=t=out:st={Seconds(Math.Max(0, length - Fade))}:d={Seconds(Fade)}";

    private static IEnumerable<string> SegmentOutput(string length, string output) => new[]
    {
        "-map", "[v]", "-map", "[a]",
        "-t", length,
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "21", "-profile:v", "high", "-pix_fmt", "yuv420p",
        "-r", Fps.ToString(CultureInfo.InvariantCulture),
        // Same timescale everywhere, so the stream-copy join keeps timestamps straight.
        "-video_track_timescale", "15360",
        "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-ac", "2",
        output,
    };

    private static string Seconds(double value) => value.ToString("0.###", CultureInfo.InvariantCulture);
}
