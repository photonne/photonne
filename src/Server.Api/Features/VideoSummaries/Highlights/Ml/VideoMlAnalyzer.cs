using System.Globalization;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;

namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// Looks at a video with the ML service: a frame every few seconds (at most
/// <see cref="MaxFrames"/>) through face detection and CLIP, and the audio
/// track through voice detection.
///
/// The ML service reads files by path from the volumes it shares with the
/// API, so frames and audio are extracted into a scratch folder inside the
/// thumbnails volume (which it mounts read-only) and removed afterwards.
///
/// Only what's missing is analysed: a previous profile's parts are kept, so a
/// capability that comes online later (voice, say) is added without redoing
/// faces.
/// </summary>
public sealed class VideoMlAnalyzer
{
    public const int MaxFrames = 20;
    private const double MinStep = 2;
    private const int MaxSeconds = 20 * 60;

    // CLIP zero-shot quality: how much more a frame looks like the first than
    // the second. Crude, but it tells a well-lit, sharp, framed shot from the
    // floor or the inside of a pocket — which is what a summary needs.
    private const string GoodShot = "a sharp, well lit, well composed photo";
    private const string BadShot = "a blurry, dark, badly framed photo";

    private static float[]? _good;
    private static float[]? _bad;

    private readonly IVideoMlService _ml;
    private readonly string _scratchRoot;
    private readonly ILogger<VideoMlAnalyzer> _logger;

    public VideoMlAnalyzer(IVideoMlService ml, IConfiguration configuration, ILogger<VideoMlAnalyzer> logger)
    {
        _ml = ml;
        _scratchRoot = Path.Combine(configuration["ThumbnailsPath"] ?? "/data/thumbnails", ".video-summaries");
        _logger = logger;
    }

    /// <summary>The sampled frame times for a video of this length.</summary>
    public static double[] FrameTimes(double duration)
    {
        var length = Math.Min(duration, MaxSeconds);
        if (length <= 0) return Array.Empty<double>();
        var step = Math.Max(MinStep, length / MaxFrames);
        var times = new List<double>();
        for (var t = step / 2; t < length; t += step) times.Add(Math.Round(t, 3));
        return times.ToArray();
    }

    public async Task<VideoMlProfile?> AnalyzeAsync(
        Guid assetId, string path, double duration, MlCapabilities capabilities,
        VideoMlProfile? previous, CancellationToken ct)
    {
        var times = FrameTimes(duration);
        var faces = previous?.Faces;
        var quality = previous?.Quality;
        var embedding = previous?.Embedding;
        var speech = previous?.Speech;

        var wantFaces = capabilities.Faces && faces == null;
        var wantFrames = capabilities.Embeddings && (quality == null || embedding == null);
        var wantSpeech = capabilities.Voice && speech == null;
        if (!wantFaces && !wantFrames && !wantSpeech) return previous;

        var scratch = Path.Combine(_scratchRoot, Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(scratch);
        try
        {
            if ((wantFaces || wantFrames) && times.Length > 0)
            {
                var frames = await ExtractFramesAsync(path, times, scratch, ct);
                if (frames != null)
                {
                    if (wantFaces) faces = await FacesAsync(assetId, frames, ct) ?? faces;
                    if (wantFrames)
                    {
                        var looks = await LooksAsync(assetId, frames, ct);
                        if (looks != null) (quality, embedding) = looks.Value;
                    }
                }
            }

            if (wantSpeech) speech = await SpeechAsync(assetId, path, scratch, ct) ?? speech;
        }
        finally
        {
            try { Directory.Delete(scratch, recursive: true); } catch (IOException) { }
        }

        return new VideoMlProfile(times, faces, quality, embedding, speech);
    }

    /// <summary>One JPEG per sampled time, or null when ffmpeg can't read the video.</summary>
    private async Task<List<(double Time, string Path)>?> ExtractFramesAsync(
        string path, double[] times, string scratch, CancellationToken ct)
    {
        var step = times.Length > 1 ? times[1] - times[0] : times[0] * 2;
        var error = await FfmpegProcess.RunFfmpegAsync(new[]
        {
            "-ss", Seconds(times[0]), "-t", Seconds(Math.Min(MaxSeconds, times[^1] + step)), "-i", path, "-an",
            "-vf", $"fps=1/{Seconds(step)},scale='min(640,iw)':-2",
            "-frames:v", times.Length.ToString(CultureInfo.InvariantCulture),
            "-q:v", "3", Path.Combine(scratch, "frame-%03d.jpg"),
        }, ct);
        if (error != null)
        {
            _logger.LogWarning("Could not extract frames for the summary: {Error}", error);
            return null;
        }

        var frames = new List<(double, string)>();
        for (var i = 0; i < times.Length; i++)
        {
            var file = Path.Combine(scratch, $"frame-{i + 1:000}.jpg");
            if (File.Exists(file)) frames.Add((times[i], file));
        }
        return frames.Count > 0 ? frames : null;
    }

    private async Task<List<FaceTrack>?> FacesAsync(Guid assetId, List<(double Time, string Path)> frames, CancellationToken ct)
    {
        var found = new List<(double, IReadOnlyList<FrameFace>)>();
        foreach (var (time, file) in frames)
        {
            var faces = await _ml.FacesAsync(file, assetId, ct);
            if (faces == null) return null; // try again another time rather than store half
            found.Add((time, faces));
        }
        return FaceTracks.Build(found);
    }

    private async Task<(double[] Quality, string Embedding)?> LooksAsync(
        Guid assetId, List<(double Time, string Path)> frames, CancellationToken ct)
    {
        _good ??= await _ml.EmbedTextAsync(GoodShot, ct) is { } g ? Vectors.Normalize(g) : null;
        _bad ??= await _ml.EmbedTextAsync(BadShot, ct) is { } b ? Vectors.Normalize(b) : null;
        if (_good == null || _bad == null) return null;

        var vectors = new List<float[]>();
        foreach (var (_, file) in frames)
        {
            var vector = await _ml.EmbedImageAsync(file, assetId, ct);
            if (vector == null) return null;
            vectors.Add(Vectors.Normalize(vector));
        }

        var quality = vectors
            .Select(v => Math.Round(Vectors.Similarity(v, _good) - Vectors.Similarity(v, _bad), 4))
            .ToArray();
        return (quality, Vectors.Encode(Vectors.Mean(vectors)));
    }

    private async Task<List<double[]>?> SpeechAsync(Guid assetId, string path, string scratch, CancellationToken ct)
    {
        var probe = await FfmpegProcess.ProbeAsync(path, ct);
        if (probe == null) return null;
        if (!probe.HasAudio) return new List<double[]>(); // nothing to hear: analysed, no speech

        var wav = Path.Combine(scratch, "audio.wav");
        var error = await FfmpegProcess.RunFfmpegAsync(new[]
        {
            "-t", Seconds(MaxSeconds), "-i", path, "-vn",
            "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", wav,
        }, ct);
        if (error != null) return null;

        var segments = await _ml.SpeechAsync(wav, assetId, ct);
        return segments?.Select(s => new[] { Math.Round(s[0], 3), Math.Round(s[1], 3) }).ToList();
    }

    private static string Seconds(double value) => value.ToString("0.###", CultureInfo.InvariantCulture);
}
