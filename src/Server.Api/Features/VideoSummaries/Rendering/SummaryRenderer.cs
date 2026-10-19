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
/// <param name="Caption">Shown in the lower corner for the clip's first seconds
/// (a chapter: "Julio · Barcelona").</param>
public sealed record RenderClip(
    string SourcePath, bool IsVideo, double Start, double Length, string? FallbackImagePath = null,
    string? Caption = null);

/// <summary>The finishing touches; without them the summary is cut plainly.</summary>
public sealed record RenderStyle
{
    /// <summary>Seconds each clip blends into the next. 0 fades each clip
    /// through black instead (and joins without re-encoding).</summary>
    public double Crossfade { get; init; }

    /// <summary>Opening card: the summary's title and a line under it.</summary>
    public string? Title { get; init; }

    public string? Subtitle { get; init; }

    /// <summary>TrueType font for the title and captions; without one, no text.</summary>
    public string? FontFile { get; init; }

    /// <summary>Background music, looped to the summary's length and lowered
    /// whenever the videos' own sound (someone talking) is loud.</summary>
    public string? MusicPath { get; init; }

    /// <summary>Segments blended per chapter (one re-encode each).</summary>
    public int ChunkSize { get; init; } = SummaryRenderer.ChunkSize;

    public bool HasText => FontFile != null && File.Exists(FontFile);
}

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
/// * Each clip fades in and out; cheap, and it hides the hard cuts. Or, with
///   a <see cref="RenderStyle.Crossfade"/>, clips blend into each other: then
///   the segments are joined in chapters of up to <see cref="ChunkSize"/>
///   (one re-encode each — an ffmpeg graph with hundreds of inputs would hold
///   them all open), each chapter fading in from and out to black.
/// * Optional opening title card, captions, and music ducked under speech.
///
/// A clip that fails to render is skipped and logged — one corrupt video must
/// not cost the user the whole summary. Only when nothing renders does it fail.
/// </summary>
public sealed class SummaryRenderer
{
    private const int Fps = 30;
    private const double Fade = 0.3;
    public const double TitleSeconds = 3.5;
    private const double CaptionSeconds = 3.5;
    internal const int ChunkSize = 30;

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
        CancellationToken ct,
        RenderStyle? style = null)
    {
        style ??= new RenderStyle();
        Directory.CreateDirectory(workDirectory);
        var segments = new List<string>(clips.Count);
        var blend = style.Crossfade > 0;
        var text = style.HasText ? style.FontFile : null;

        for (var i = 0; i < clips.Count; i++)
        {
            ct.ThrowIfCancellationRequested();
            var clip = clips[i];
            var segment = Path.Combine(workDirectory, $"segment-{i:00000}.mp4");

            string? error;
            try
            {
                var look = new SegmentLook(f, Fades: !blend, text, clip.Caption, workDirectory, i);
                error = clip.IsVideo
                    ? await RenderVideoSegmentAsync(clip, segment, look, ct)
                    : await RenderPhotoSegmentAsync(clip, segment, look, ct);
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                error = ex.Message;
            }

            if (error == null && File.Exists(segment)) segments.Add(segment);
            else _logger.LogWarning("Summary clip skipped ({Source}): {Error}", clip.SourcePath, error);

            // Segments are the expensive part; joins are copies or one re-encode.
            await progress((blend ? 0.75 : 0.95) * (i + 1) / clips.Count);
        }

        if (segments.Count == 0)
            throw new InvalidOperationException("No se pudo procesar ningún vídeo ni foto.");
        var rendered = segments.Count;

        if (style.Title != null && text != null)
        {
            var card = Path.Combine(workDirectory, "title.mp4");
            var cardError = await RenderTitleCardAsync(segments[0], card, f, style, text, workDirectory, ct);
            if (cardError == null) segments.Insert(0, card);
            else _logger.LogWarning("Summary title card skipped: {Error}", cardError);
        }

        if (blend && segments.Count > 1)
        {
            var chapters = new List<string>();
            var chunks = segments.Chunk(Math.Max(2, style.ChunkSize)).ToList();
            for (var k = 0; k < chunks.Count; k++)
            {
                var chapter = Path.Combine(workDirectory, $"chapter-{k:000}.mp4");
                var error = await BlendAsync(chunks[k], chapter, style.Crossfade, ct);
                if (error != null) throw new InvalidOperationException("No se pudieron encadenar los fragmentos: " + error);
                chapters.Add(chapter);
                await progress(0.75 + 0.2 * (k + 1) / chunks.Count);
            }
            segments = chapters;
        }

        var joined = style.MusicPath != null ? Path.Combine(workDirectory, "joined.mp4") : outputPath;
        var list = Path.Combine(workDirectory, "segments.txt");
        await File.WriteAllLinesAsync(list,
            segments.Select(s => $"file '{s.Replace("'", "'\\''")}'"), ct);

        var metadata = new[]
        {
            "-map_metadata", "-1",
            "-metadata", "title=" + title,
            "-metadata", "creation_time=" + creationTimeUtc.ToString("yyyy-MM-dd'T'HH:mm:ss'.000000Z'", CultureInfo.InvariantCulture),
            "-movflags", "+faststart",
        };
        var joinError = await FfmpegProcess.RunFfmpegAsync(
            new[] { "-f", "concat", "-safe", "0", "-i", list, "-map", "0", "-c", "copy" }
                .Concat(metadata).Append(joined),
            ct);
        if (joinError != null)
            throw new InvalidOperationException("No se pudo unir el vídeo final: " + joinError);

        if (style.MusicPath != null)
        {
            var musicError = await AddMusicAsync(joined, style.MusicPath, outputPath, metadata, ct);
            if (musicError != null)
            {
                // The summary is still worth having without its music.
                _logger.LogWarning("Summary music skipped: {Error}", musicError);
                File.Move(joined, outputPath, overwrite: true);
            }
        }

        await progress(1);
        return rendered;
    }

    /// <summary>How one segment looks: frame, fades, caption.</summary>
    private sealed record SegmentLook(Frame Frame, bool Fades, string? FontFile, string? Caption, string WorkDirectory, int Index);

    /// <summary>
    /// Blends a run of segments into one: each crossfades into the next
    /// (picture and sound), and the whole fades in from and out to black.
    /// </summary>
    private static async Task<string?> BlendAsync(IReadOnlyList<string> segments, string output, double crossfade, CancellationToken ct)
    {
        var durations = new List<double>();
        foreach (var segment in segments)
            durations.Add((await FfmpegProcess.ProbeAsync(segment, ct))?.DurationSeconds ?? 0);

        // A crossfade can't be longer than either side of it.
        var d = Math.Min(crossfade, durations.Min() / 2);
        var graph = new System.Text.StringBuilder();
        string video = "[0:v]", audio = "[0:a]";
        var offset = 0.0;
        for (var i = 1; i < segments.Count; i++)
        {
            offset += durations[i - 1] - d;
            graph.Append($"{video}[{i}:v]xfade=transition=fade:duration={Seconds(d)}:offset={Seconds(offset)}[v{i}];");
            graph.Append($"{audio}[{i}:a]acrossfade=d={Seconds(d)}[a{i}];");
            video = $"[v{i}]";
            audio = $"[a{i}]";
        }
        var total = durations.Sum() - d * (segments.Count - 1);
        var fadeOut = Seconds(Math.Max(0, total - 0.4));
        graph.Append($"{video}fade=t=in:st=0:d=0.4,fade=t=out:st={fadeOut}:d=0.4,format=yuv420p[v];");
        graph.Append($"{audio}afade=t=in:st=0:d=0.4,afade=t=out:st={fadeOut}:d=0.4[a]");

        var args = new List<string>();
        foreach (var segment in segments) args.AddRange(new[] { "-i", segment });
        args.AddRange(new[] { "-filter_complex", graph.ToString() });
        args.AddRange(SegmentOutput(Seconds(total), output));
        return await FfmpegProcess.RunFfmpegAsync(args, ct);
    }

    /// <summary>
    /// The opening card: the summary's first picture, blurred and darkened,
    /// with the title and a line under it fading in.
    /// </summary>
    private static async Task<string?> RenderTitleCardAsync(
        string firstSegment, string output, Frame f, RenderStyle style, string font, string workDirectory, CancellationToken ct)
    {
        var background = Path.Combine(workDirectory, "title-background.jpg");
        var grab = await FfmpegProcess.RunFfmpegAsync(new[] { "-ss", "0.5", "-i", firstSegment, "-frames:v", "1", background }, ct);
        if (grab != null) return grab;

        var shortSide = Math.Min(f.Width, f.Height);
        var titleFile = await TextFileAsync(workDirectory, "title.txt", style.Title!, ct);
        var titleSize = FitFontSize(style.Title!, shortSide * 0.08, f.Width * 0.88);
        var length = Seconds(TitleSeconds);
        var appear = $"alpha='if(lt(t,0.3),0,if(lt(t,1.1),(t-0.3)/0.8,1))'";
        var graph = new System.Text.StringBuilder(
            $"[0:v]scale={f.Width}:{f.Height},boxblur=20:3,eq=brightness=-0.3,setsar=1," +
            $"drawtext=fontfile={FfmpegProcess.EscapeFilterPath(font)}:textfile={FfmpegProcess.EscapeFilterPath(titleFile)}:expansion=none:" +
            $"fontcolor=white:fontsize={titleSize}:x=(w-tw)/2:y=(h/2)-th-{shortSide / 60}:{appear}:shadowcolor=black@0.5:shadowx=2:shadowy=2");
        if (!string.IsNullOrWhiteSpace(style.Subtitle))
        {
            var subtitleFile = await TextFileAsync(workDirectory, "subtitle.txt", style.Subtitle, ct);
            var subtitleSize = FitFontSize(style.Subtitle, shortSide * 0.04, f.Width * 0.88);
            graph.Append(
                $",drawtext=fontfile={FfmpegProcess.EscapeFilterPath(font)}:textfile={FfmpegProcess.EscapeFilterPath(subtitleFile)}:expansion=none:" +
                $"fontcolor=white@0.85:fontsize={subtitleSize}:x=(w-tw)/2:y=(h/2)+{shortSide / 40}:{appear}");
        }
        graph.Append($",fps={Fps},format=yuv420p[v];[1:a]anull[a]");

        try
        {
            return await FfmpegProcess.RunFfmpegAsync(
                new[]
                {
                    "-loop", "1", "-t", length, "-i", background,
                    "-f", "lavfi", "-t", length, "-i", "anullsrc=r=48000:cl=stereo",
                    "-filter_complex", graph.ToString(),
                }.Concat(SegmentOutput(length, output)),
                ct);
        }
        finally
        {
            File.Delete(background);
        }
    }

    /// <summary>
    /// Lays the music under the summary: looped to its length, faded in and
    /// out, and pushed down (sidechain compression) whenever the summary's own
    /// sound is loud — so people talking stay audible. The picture is copied.
    /// </summary>
    private static async Task<string?> AddMusicAsync(
        string video, string music, string output, IEnumerable<string> metadata, CancellationToken ct)
    {
        var total = (await FfmpegProcess.ProbeAsync(video, ct))?.DurationSeconds ?? 0;
        if (total <= 0) return "could not read the joined video";
        var t = Seconds(total);
        var fadeOut = Seconds(Math.Max(0, total - 3));
        var graph =
            $"[1:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo,atrim=0:{t},asetpts=PTS-STARTPTS," +
            $"afade=t=in:st=0:d=1.5,afade=t=out:st={fadeOut}:d=3,volume=0.7[m];" +
            "[0:a]asplit=2[own][key];" +
            "[m][key]sidechaincompress=threshold=0.03:ratio=8:attack=40:release=600[ducked];" +
            "[own][ducked]amix=inputs=2:duration=first:normalize=0[a]";

        return await FfmpegProcess.RunFfmpegAsync(
            new[]
            {
                "-i", video, "-stream_loop", "-1", "-i", music,
                "-filter_complex", graph,
                "-map", "0:v", "-map", "[a]", "-c:v", "copy",
                "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2", "-t", t,
            }.Concat(metadata).Append(output),
            ct);
    }

    /// <summary>A caption drawn in the lower corner for the clip's first seconds.</summary>
    private static async Task<string> CaptionFilterAsync(SegmentLook look, CancellationToken ct)
    {
        if (look.FontFile == null || string.IsNullOrWhiteSpace(look.Caption)) return "";
        var file = await TextFileAsync(look.WorkDirectory, $"caption-{look.Index:00000}.txt", look.Caption, ct);
        var shortSide = Math.Min(look.Frame.Width, look.Frame.Height);
        var size = FitFontSize(look.Caption, shortSide * 0.045, look.Frame.Width * 0.85);
        var c = Seconds(CaptionSeconds);
        return $",drawtext=fontfile={FfmpegProcess.EscapeFilterPath(look.FontFile)}:textfile={FfmpegProcess.EscapeFilterPath(file)}:expansion=none:" +
               $"fontcolor=white:fontsize={size}:x=w*0.05:y=h-th-h*0.07:shadowcolor=black@0.7:shadowx=2:shadowy=2:" +
               $"alpha='if(lt(t,0.5),t/0.5,if(lt(t,{Seconds(CaptionSeconds - 0.5)}),1,if(lt(t,{c}),({c}-t)/0.5,0)))'";
    }

    private static async Task<string> TextFileAsync(string directory, string name, string text, CancellationToken ct)
    {
        var path = Path.Combine(directory, name);
        await File.WriteAllTextAsync(path, text.Trim(), new System.Text.UTF8Encoding(false), ct);
        return path;
    }

    /// <summary>A font size that keeps the text on one line within the width:
    /// glyphs average a little over half their size across.</summary>
    internal static int FitFontSize(string text, double preferred, double width) =>
        (int)Math.Max(12, Math.Min(preferred, width / (0.58 * Math.Max(1, text.Trim().Length))));

    private static async Task<string?> RenderVideoSegmentAsync(RenderClip clip, string output, SegmentLook look, CancellationToken ct)
    {
        var probe = await FfmpegProcess.ProbeAsync(clip.SourcePath, ct);
        if (probe is not { HasVideo: true }) return "no video stream";

        var caption = await CaptionFilterAsync(look, ct);
        var error = await RunVideoSegmentAsync(clip, output, probe, look, caption, toneMap: probe.IsHdr, ct);
        // zscale isn't in every ffmpeg build. Grey colours beat a missing clip.
        if (error != null && probe.IsHdr && error.Contains("zscale", StringComparison.OrdinalIgnoreCase))
            error = await RunVideoSegmentAsync(clip, output, probe, look, caption, toneMap: false, ct);
        return error;
    }

    private static Task<string?> RunVideoSegmentAsync(
        RenderClip clip, string output, MediaProbe probe, SegmentLook look, string caption, bool toneMap, CancellationToken ct)
    {
        var f = look.Frame;
        var length = Seconds(clip.Length);
        var hdrToSdr = toneMap
            ? "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,"
            : "";
        var audioSource = probe.HasAudio ? "[0:a]" : "[1:a]";
        var graph =
            $"[0:v]{hdrToSdr}{FitFilter("v", f)},fps={Fps},format=yuv420p{VideoFades(clip.Length, look.Fades)}{caption}[v];" +
            $"{audioSource}{AudioChain(clip.Length, normalise: probe.HasAudio, look.Fades)}[a]";

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
        RenderClip clip, string output, SegmentLook look, CancellationToken ct)
    {
        var f = look.Frame;
        var index = look.Index;
        var frame = Path.Combine(look.WorkDirectory, $"frame-{index:00000}.jpg");
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
        var caption = await CaptionFilterAsync(look, ct);
        // Upscaled before zoompan: it moves in whole pixels, which judders at 1080p.
        var graph =
            $"[0:v]scale={f.Width * 2}:{f.Height * 2},zoompan=z='{z}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'" +
            $":d={frames}:s={f.Width}x{f.Height}:fps={Fps},setsar=1,format=yuv420p{VideoFades(clip.Length, look.Fades)}{caption}[v];" +
            $"[1:a]{AudioChain(clip.Length, normalise: false, look.Fades)}[a]";

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

    /// <summary>Fade through black at both ends — when clips aren't crossfaded.</summary>
    private static string VideoFades(double length, bool fades) => !fades ? "" :
        $",fade=t=in:st=0:d={Seconds(Fade)},fade=t=out:st={Seconds(Math.Max(0, length - Fade))}:d={Seconds(Fade)}";

    private static string AudioChain(double length, bool normalise, bool fades) =>
        (normalise ? "loudnorm=I=-18:TP=-2:LRA=11," : "") +
        $"aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo,apad,atrim=0:{Seconds(length)}" +
        (fades
            ? $",afade=t=in:st=0:d={Seconds(Fade)},afade=t=out:st={Seconds(Math.Max(0, length - Fade))}:d={Seconds(Fade)}"
            : "");

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
