using System.Diagnostics;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Xabe.FFmpeg;

namespace Photonne.Server.Api.Shared.Services.Video;

/// <summary>
/// Makes the playback copies (<see cref="AssetPlayback"/>), one video at a
/// time: transcoding is the heaviest thing the server does, and a library
/// import must not turn into twenty ffmpegs at once. Copies live under
/// <c>{Video:PlaybackPath}/{assetId}.mp4</c> (<see cref="VideoPlaybackStorage"/>),
/// and stop while that disk is down to its reserve.
/// </summary>
public sealed class VideoPlaybackService(
    ApplicationDbContext db,
    SettingsService settings,
    IConfiguration configuration,
    ILogger<VideoPlaybackService> logger)
{
    /// <summary>Tries before a video is left on its original for good.</summary>
    public const int MaxAttempts = 3;
    private static readonly TimeSpan RetryAfter = TimeSpan.FromHours(6);

    private readonly VideoPlaybackStorage storage = VideoPlaybackStorage.From(configuration);

    public string PlaybackRoot => storage.Root;

    /// <summary>
    /// Set when the last <see cref="ProcessNextAsync"/> stopped because the
    /// disk is down to its reserve: free bytes, or null when they can't be read.
    /// </summary>
    public (long? AvailableBytes, long MinFreeBytes)? OutOfSpace { get; private set; }

    private readonly VideoTranscodeOptions options = VideoTranscodeOptions.From(configuration, logger);

    /// <summary>
    /// Looks at the next video without a decision; false when there is none,
    /// or when there's no room for its copy (<see cref="OutOfSpace"/>): the
    /// video keeps its place in the queue and nothing is marked failed.
    /// </summary>
    public async Task<bool> ProcessNextAsync(CancellationToken ct)
    {
        OutOfSpace = null;
        if (!HasRoom(0)) return false;

        var retryBefore = DateTime.UtcNow - RetryAfter;
        var next = await db.Assets
            .Where(a => a.Type == AssetType.Video && a.DeletedAt == null && !a.IsFileMissing)
            .Where(a => !db.AssetPlaybacks.Any(p => p.AssetId == a.Id)
                        || db.AssetPlaybacks.Any(p => p.AssetId == a.Id && p.Status == PlaybackStatus.Failed
                                                      && p.Attempts < MaxAttempts && p.UpdatedAt < retryBefore))
            .OrderByDescending(a => a.CapturedAt)
            .Select(a => new { a.Id, a.FullPath })
            .FirstOrDefaultAsync(ct);
        if (next is null) return false;

        var row = await db.AssetPlaybacks.FindAsync([next.Id], ct);
        if (row is null)
        {
            row = new AssetPlayback { AssetId = next.Id };
            db.AssetPlaybacks.Add(row);
        }

        try
        {
            var source = await settings.ResolvePhysicalPathAsync(next.FullPath);
            var media = await FFmpeg.GetMediaInfo(source, ct);
            var video = media.VideoStreams.FirstOrDefault();
            var probe = new VideoProbe(
                Path.GetExtension(source),
                video?.Codec,
                video?.Width ?? 0,
                video?.Height ?? 0,
                media.AudioStreams.FirstOrDefault()?.Codec);

            if (video is null)
            {
                Fail(row, "no video stream");
            }
            else if (!VideoPlaybackPolicy.NeedsPlaybackCopy(probe))
            {
                row.Status = PlaybackStatus.NotNeeded;
                row.FilePath = null;
            }
            else if (!HasRoom(new FileInfo(source).Length))
            {
                // Untouched, so it's picked first again once there is room.
                if (db.Entry(row).State == EntityState.Added) db.Entry(row).State = EntityState.Detached;
                return false;
            }
            else
            {
                var target = Path.Combine(PlaybackRoot, $"{next.Id}.mp4");
                var hasAudio = probe.AudioCodec is not null;
                var done = await TranscodeAsync(source, target, hasAudio, options, ct);
                if (!done && options.Encoder != VideoEncoder.Software)
                {
                    // No GPU passed through, a driver without NVENC, a format the
                    // card can't take: the CPU still makes the copy.
                    logger.LogWarning("{Encoder} failed for {AssetId}; retrying with libx264", options.Encoder, next.Id);
                    done = await TranscodeAsync(source, target, hasAudio, options with { Encoder = VideoEncoder.Software }, ct);
                }
                if (done)
                {
                    row.Status = PlaybackStatus.Ready;
                    row.FilePath = target;
                }
                else
                {
                    Fail(row, "ffmpeg failed");
                }
            }
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "Playback copy of {AssetId} failed", next.Id);
            Fail(row, ex.Message);
        }

        row.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return true;
    }

    private bool HasRoom(long sourceBytes)
    {
        if (storage.MinFreeBytes <= 0) return true;
        var available = storage.AvailableBytes();
        if (available is { } bytes && storage.HasRoom(bytes, sourceBytes)) return true;
        OutOfSpace = (available, storage.MinFreeBytes);
        return false;
    }

    private static void Fail(AssetPlayback row, string reason)
    {
        row.Status = PlaybackStatus.Failed;
        row.FilePath = null;
        row.Attempts++;
    }

    /// <summary>
    /// Deletes copies whose video is gone (the row went with the asset). A
    /// copy missing from where its row says is looked for in
    /// <see cref="PlaybackRoot"/> — the folder was moved along with a new
    /// <c>Video:PlaybackPath</c> — and otherwise forgotten (deleted by hand to
    /// free space) so it is made again; meanwhile the original is played.
    /// </summary>
    public async Task<int> SweepOrphansAsync(CancellationToken ct)
    {
        var missing = (await db.AssetPlaybacks
                .Where(p => p.Status == PlaybackStatus.Ready)
                .Select(p => new { p.AssetId, p.FilePath })
                .ToListAsync(ct))
            .Where(p => p.FilePath is null || !File.Exists(p.FilePath))
            .Select(p => p.AssetId)
            .ToList();
        if (missing.Count > 0)
        {
            var rows = await db.AssetPlaybacks.Where(p => missing.Contains(p.AssetId)).ToListAsync(ct);
            foreach (var row in rows)
            {
                var here = Path.Combine(PlaybackRoot, $"{row.AssetId}.mp4");
                if (File.Exists(here)) row.FilePath = here;
                else db.AssetPlaybacks.Remove(row);
            }
            await db.SaveChangesAsync(ct);
        }

        if (!Directory.Exists(PlaybackRoot)) return 0;
        var files = Directory.GetFiles(PlaybackRoot, "*.mp4");
        var ids = files
            .Select(f => Guid.TryParse(Path.GetFileNameWithoutExtension(f), out var id) ? id : (Guid?)null)
            .OfType<Guid>()
            .ToList();
        var kept = (await db.AssetPlaybacks
            .Where(p => ids.Contains(p.AssetId) && p.Status == PlaybackStatus.Ready)
            .Select(p => p.AssetId)
            .ToListAsync(ct)).ToHashSet();

        var removed = 0;
        foreach (var file in files)
        {
            if (Guid.TryParse(Path.GetFileNameWithoutExtension(file), out var id) && kept.Contains(id)) continue;
            try
            {
                File.Delete(file);
                removed++;
            }
            catch (IOException) { }
        }
        return removed;
    }

    /// <summary>
    /// Encodes as <see cref="VideoTranscodeOptions.Arguments"/> says. Written
    /// to a temp name and moved, so a half-written copy is never served.
    /// </summary>
    private async Task<bool> TranscodeAsync(string source, string target, bool hasAudio, VideoTranscodeOptions encoding, CancellationToken ct)
    {
        Directory.CreateDirectory(PlaybackRoot);
        var temp = target + ".part";
        var arguments = encoding.Arguments(source, temp, hasAudio);

        var ffmpegExe = OperatingSystem.IsWindows() ? "ffmpeg.exe" : "ffmpeg";
        var ffmpegPath = string.IsNullOrEmpty(FFmpeg.ExecutablesPath) ? ffmpegExe : Path.Combine(FFmpeg.ExecutablesPath, ffmpegExe);
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

        try
        {
            process.Start();
            var stderr = process.StandardError.ReadToEndAsync(ct);
            await process.WaitForExitAsync(ct);
            var error = await stderr;
            if (process.ExitCode != 0 || !File.Exists(temp))
            {
                logger.LogWarning("ffmpeg exited {ExitCode} for {Source}: {Error}", process.ExitCode, source, error);
                return false;
            }
            File.Move(temp, target, overwrite: true);
            return true;
        }
        catch (OperationCanceledException)
        {
            try { if (!process.HasExited) process.Kill(entireProcessTree: true); } catch { }
            throw;
        }
        finally
        {
            try { if (File.Exists(temp)) File.Delete(temp); } catch { }
        }
    }
}

/// <summary>
/// Works through <see cref="VideoPlaybackService"/> in the background: video
/// after video until none is left, then a look every few minutes for new
/// ones and stale copies. Off with <c>Video:PlaybackCopies=false</c> (env
/// <c>Video__PlaybackCopies=false</c>) on a server too weak to transcode;
/// <see cref="VideoTranscodeOptions"/> caps the threads or moves it to the GPU.
/// </summary>
public sealed class VideoPlaybackWorker(
    IServiceScopeFactory scopeFactory,
    IConfiguration configuration,
    ILogger<VideoPlaybackWorker> logger) : BackgroundService
{
    private static readonly TimeSpan StartupDelay = TimeSpan.FromMinutes(2);
    private static readonly TimeSpan IdleInterval = TimeSpan.FromMinutes(5);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!configuration.GetValue("Video:PlaybackCopies", true))
        {
            logger.LogInformation("Video playback copies are off (Video:PlaybackCopies=false)");
            return;
        }
        var encoding = VideoTranscodeOptions.From(configuration, logger);
        var storage = VideoPlaybackStorage.From(configuration);
        logger.LogInformation("Video playback copies: encoder {Encoder}, threads {Threads}, in {Root}, keeping {MinFreeGb} GB free",
            encoding.Encoder, encoding.Threads > 0 ? encoding.Threads.ToString() : "auto",
            storage.Root, storage.MinFreeBytes / (1024 * 1024 * 1024));
        var outOfSpace = false;

        try
        {
            await Task.Delay(StartupDelay, stoppingToken);
            while (!stoppingToken.IsCancellationRequested)
            {
                try
                {
                    using var scope = scopeFactory.CreateScope();
                    var service = scope.ServiceProvider.GetRequiredService<VideoPlaybackService>();
                    var processed = await service.ProcessNextAsync(stoppingToken);
                    if (service.OutOfSpace is { } space)
                    {
                        // Once per pause, not every five minutes.
                        if (!outOfSpace)
                            logger.LogWarning(
                                "Video playback copies paused: {Root} has {AvailableGb} GB free and keeps {MinFreeGb} GB (Video:MinFreeSpaceGb)",
                                storage.Root,
                                space.AvailableBytes is { } bytes ? (bytes / (1024.0 * 1024 * 1024)).ToString("0.#") : "?",
                                space.MinFreeBytes / (1024 * 1024 * 1024));
                        outOfSpace = true;
                    }
                    else if (outOfSpace)
                    {
                        logger.LogInformation("Video playback copies resumed: there is room in {Root} again", storage.Root);
                        outOfSpace = false;
                    }
                    if (processed) continue;
                    await service.SweepOrphansAsync(stoppingToken);
                }
                catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                {
                    throw;
                }
                catch (Exception ex)
                {
                    logger.LogWarning(ex, "Video playback sweep failed; retrying in {Interval}", IdleInterval);
                }
                await Task.Delay(IdleInterval, stoppingToken);
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Graceful shutdown: an interrupted copy is redone next time.
        }
    }
}
