using System.Collections.Concurrent;
using System.Diagnostics;
using SixLabors.ImageSharp;
using Xabe.FFmpeg;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>Preview frames of one motion clip, decoded and ready to serve.</summary>
public record MotionFrameSet(string FramesDirectory, int FrameCount)
{
    /// <summary>Path of the preview JPEG for the 0-based <paramref name="index"/>
    /// (ffmpeg numbers its output from 1).</summary>
    public string FramePath(int index) =>
        Path.Combine(FramesDirectory, $"{index + 1:00000}.jpg");
}

/// <summary>
/// Frame-by-frame access to the clip of a motion photo (iOS Live Photo sibling
/// <c>.mov</c> or the MP4 Samsung/Google embed in the still), so the user can
/// pick a clean frame and keep it as a photo of its own.
///
/// Browsing works on PREVIEW frames: the whole clip is decoded once with ffmpeg
/// into a temp folder (a few seconds of video, ~90 JPEGs) and each frame is then
/// a plain file read — stepping one by one from the client has to be instant,
/// and a decode per request would not be. The folder is keyed by asset and file
/// mtime and swept after <see cref="CacheLifetime"/> without use.
///
/// Saving decodes the chosen frame again at the clip's full resolution. Both
/// passes count frames the same way (every decoded frame, no fps resampling), so
/// a preview index names the same picture in the full-resolution pass.
/// </summary>
public class MotionFrameService
{
    /// <summary>Longest side of the preview frames: enough for a phone screen
    /// without making the first decode slow.</summary>
    private const int PreviewLongSide = 1080;
    private static readonly TimeSpan CacheLifetime = TimeSpan.FromHours(1);
    private static readonly ConcurrentDictionary<string, SemaphoreSlim> Gates = new();

    private readonly string _cacheRoot = Path.Combine(Path.GetTempPath(), "photonne-motion-frames");
    private readonly ILogger<MotionFrameService> _logger;

    public MotionFrameService(ILogger<MotionFrameService> logger)
    {
        _logger = logger;
    }

    /// <summary>
    /// Returns the path to the sibling motion clip ({basename}.mov / .MOV) next
    /// to <paramref name="stillPath"/>, or null when none exists. Tries the
    /// common casings explicitly since the host filesystem may be case-sensitive.
    /// </summary>
    public static string? ResolveSiblingClipPath(string stillPath)
    {
        var directory = Path.GetDirectoryName(stillPath);
        if (string.IsNullOrEmpty(directory))
        {
            return null;
        }

        var baseName = Path.GetFileNameWithoutExtension(stillPath);
        foreach (var ext in new[] { ".mov", ".MOV", ".mp4", ".MP4" })
        {
            var candidate = Path.Combine(directory, baseName + ext);
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        return null;
    }

    /// <summary>
    /// Decodes (or reuses) the preview frames of the still at
    /// <paramref name="stillPath"/>. Null when the asset has no motion clip or
    /// ffmpeg could not decode a single frame of it.
    /// </summary>
    public async Task<MotionFrameSet?> GetPreviewFramesAsync(
        Guid assetId, string stillPath, CancellationToken cancellationToken)
    {
        if (!File.Exists(stillPath)) return null;

        var key = $"{assetId:N}-{File.GetLastWriteTimeUtc(stillPath).Ticks}";
        var directory = Path.Combine(_cacheRoot, key);
        var framesDirectory = Path.Combine(directory, "frames");
        var marker = Path.Combine(directory, "count");

        var gate = Gates.GetOrAdd(key, _ => new SemaphoreSlim(1, 1));
        await gate.WaitAsync(cancellationToken);
        try
        {
            if (File.Exists(marker) &&
                int.TryParse(await File.ReadAllTextAsync(marker, cancellationToken), out var cached))
            {
                Directory.SetLastWriteTimeUtc(directory, DateTime.UtcNow);
                return new MotionFrameSet(framesDirectory, cached);
            }

            SweepStale();
            if (Directory.Exists(directory)) Directory.Delete(directory, recursive: true);
            Directory.CreateDirectory(framesDirectory);

            var clip = await MaterializeClipAsync(stillPath, directory, cancellationToken);
            if (clip == null)
            {
                Directory.Delete(directory, recursive: true);
                return null;
            }

            var scale = $"scale=w='min({PreviewLongSide},iw)':h='min({PreviewLongSide},ih)':force_original_aspect_ratio=decrease";
            var decoded = await RunFfmpegAsync(
                $"-i \"{clip}\" -an -fps_mode passthrough -vf \"{scale}\" -q:v 4 \"{Path.Combine(framesDirectory, "%05d.jpg")}\"",
                cancellationToken);
            var count = Directory.GetFiles(framesDirectory, "*.jpg").Length;
            if (!decoded || count == 0)
            {
                Directory.Delete(directory, recursive: true);
                return null;
            }

            await File.WriteAllTextAsync(marker, count.ToString(), cancellationToken);
            return new MotionFrameSet(framesDirectory, count);
        }
        finally
        {
            gate.Release();
        }
    }

    /// <summary>
    /// Decodes frame <paramref name="index"/> (0-based) of the clip at full
    /// resolution, already rotated upright. Null when the asset has no clip or
    /// the clip has no such frame.
    /// </summary>
    public async Task<Image?> ExtractFullFrameAsync(
        string stillPath, int index, CancellationToken cancellationToken)
    {
        var workDirectory = Path.Combine(_cacheRoot, "save-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(workDirectory);
        try
        {
            var clip = await MaterializeClipAsync(stillPath, workDirectory, cancellationToken);
            if (clip == null) return null;

            // PNG keeps the frame lossless until the single JPEG encode that
            // carries the metadata.
            var output = Path.Combine(workDirectory, "frame.png");
            await RunFfmpegAsync(
                $"-i \"{clip}\" -an -vf \"select=eq(n\\,{index})\" -fps_mode passthrough -frames:v 1 \"{output}\"",
                cancellationToken);
            if (!File.Exists(output)) return null;

            return await Image.LoadAsync(output, cancellationToken);
        }
        finally
        {
            try { Directory.Delete(workDirectory, recursive: true); }
            catch (Exception ex) { _logger.LogWarning(ex, "Could not delete {Directory}", workDirectory); }
        }
    }

    /// <summary>
    /// A path ffmpeg can open: the sibling clip as is, or the MP4 embedded in
    /// the still copied out to <paramref name="workDirectory"/> (same slice
    /// <c>MotionPhotoEndpoint</c> streams).
    /// </summary>
    private static async Task<string?> MaterializeClipAsync(
        string stillPath, string workDirectory, CancellationToken cancellationToken)
    {
        var sibling = ResolveSiblingClipPath(stillPath);
        if (sibling != null) return sibling;

        if (EmbeddedMotionPhotoExtractor.ResolveEmbeddedVideo(stillPath) is not { } range) return null;

        var clipPath = Path.Combine(workDirectory, "clip.mp4");
        await using var source = File.OpenRead(stillPath);
        source.Position = range.Offset;
        await using var target = File.Create(clipPath);
        await source.CopyToAsync(target, cancellationToken);
        return clipPath;
    }

    private void SweepStale()
    {
        if (!Directory.Exists(_cacheRoot)) return;
        var cutoff = DateTime.UtcNow - CacheLifetime;
        foreach (var directory in Directory.GetDirectories(_cacheRoot))
        {
            if (Directory.GetLastWriteTimeUtc(directory) >= cutoff) continue;
            try { Directory.Delete(directory, recursive: true); }
            catch (Exception ex) { _logger.LogWarning(ex, "Could not sweep {Directory}", directory); }
        }
    }

    private async Task<bool> RunFfmpegAsync(string arguments, CancellationToken cancellationToken)
    {
        var ffmpegExe = OperatingSystem.IsWindows() ? "ffmpeg.exe" : "ffmpeg";
        var ffmpegPath = string.IsNullOrEmpty(FFmpeg.ExecutablesPath)
            ? ffmpegExe
            : Path.Combine(FFmpeg.ExecutablesPath, ffmpegExe);

        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = ffmpegPath,
                Arguments = "-hide_banner -loglevel error -y " + arguments,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            }
        };

        process.Start();
        // Drain stderr so a chatty ffmpeg can't deadlock on a full pipe buffer.
        var stderr = process.StandardError.ReadToEndAsync(cancellationToken);
        await process.WaitForExitAsync(cancellationToken);
        var error = await stderr;

        if (process.ExitCode != 0)
        {
            _logger.LogWarning("ffmpeg exited {ExitCode} ({Arguments}): {Error}", process.ExitCode, arguments, error);
            return false;
        }

        return true;
    }
}
