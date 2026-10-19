using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.UploadAssets;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.VideoSummaries;

/// <summary>
/// Renders one requested <see cref="VideoSummary"/> end to end: resolves its
/// material, plans the cut, renders it, and files the result in the
/// requester's library under <see cref="FolderName"/> as an ordinary asset.
/// </summary>
public sealed class VideoSummaryGenerator
{
    public const string FolderName = "Recuerdos";

    /// <summary>Share of the progress bar for probing and measuring the videos;
    /// rendering takes the rest. Measuring only happens the first time.</summary>
    private const double AnalysisShare = 0.3;

    private readonly ApplicationDbContext _db;
    private readonly VideoSummarySources _sources;
    private readonly SettingsService _settings;
    private readonly UserStorageService _userStorage;
    private readonly AssetIndexingService _indexing;
    private readonly IEnrichmentService _enrichment;
    private readonly FileHashService _hashes;
    private readonly SummaryRenderer _renderer;
    private readonly INotificationService _notifications;
    private readonly ILogger<VideoSummaryGenerator> _logger;

    public VideoSummaryGenerator(
        ApplicationDbContext db,
        VideoSummarySources sources,
        SettingsService settings,
        UserStorageService userStorage,
        AssetIndexingService indexing,
        IEnrichmentService enrichment,
        FileHashService hashes,
        SummaryRenderer renderer,
        INotificationService notifications,
        ILogger<VideoSummaryGenerator> logger)
    {
        _db = db;
        _sources = sources;
        _settings = settings;
        _userStorage = userStorage;
        _indexing = indexing;
        _enrichment = enrichment;
        _hashes = hashes;
        _renderer = renderer;
        _notifications = notifications;
        _logger = logger;
    }

    public async Task RunAsync(Guid summaryId, CancellationToken ct)
    {
        var summary = await _db.VideoSummaries.FirstOrDefaultAsync(s => s.Id == summaryId, ct);
        if (summary is not { Status: VideoSummaryStatus.Pending }) return;

        summary.Status = VideoSummaryStatus.Running;
        summary.Progress = 0;
        summary.Error = null;
        await _db.SaveChangesAsync(ct);

        var workDirectory = Path.Combine(Path.GetTempPath(), "photonne-summaries", summary.Id.ToString("N"));
        try
        {
            var asset = await GenerateAsync(summary, workDirectory, ct);

            summary.Status = VideoSummaryStatus.Completed;
            summary.Progress = 1;
            summary.AssetId = asset.Id;
            summary.CompletedAt = DateTime.UtcNow;
            await _db.SaveChangesAsync(ct);

            await _notifications.CreateAsync(
                summary.OwnerId,
                NotificationType.JobCompleted,
                "Vídeo resumen listo",
                $"«{summary.Title}» ya está en tu biblioteca, en la carpeta {FolderName}.",
                $"/videos?asset={asset.Id}");
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // Shutdown mid-render: back to pending, the worker picks it up on start.
            summary.Status = VideoSummaryStatus.Pending;
            summary.Progress = 0;
            await _db.SaveChangesAsync(CancellationToken.None);
            throw;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Video summary {SummaryId} failed", summary.Id);
            var reason = ex is VideoSummaryException ? ex.Message : "Error inesperado al generar el vídeo.";

            summary.Status = VideoSummaryStatus.Failed;
            summary.Error = reason.Length > 500 ? reason[..500] : reason;
            summary.CompletedAt = DateTime.UtcNow;
            await _db.SaveChangesAsync(CancellationToken.None);

            await _notifications.CreateAsync(
                summary.OwnerId,
                NotificationType.JobFailed,
                "No se pudo crear el vídeo resumen",
                $"«{summary.Title}»: {reason}");
        }
        finally
        {
            try
            {
                if (Directory.Exists(workDirectory)) Directory.Delete(workDirectory, recursive: true);
            }
            catch (IOException) { /* temp dir; swept by the OS eventually */ }
        }
    }

    private async Task<Asset> GenerateAsync(VideoSummary summary, string workDirectory, CancellationToken ct)
    {
        var query = summary.Source switch
        {
            VideoSummarySource.Year => await _sources.ForYearAsync(summary.OwnerId, summary.Year!.Value, ct),
            VideoSummarySource.Album => await _sources.ForAlbumAsync(summary.OwnerId, summary.AlbumId!.Value, ct),
            _ => null,
        } ?? throw new VideoSummaryException("El álbum ya no existe o ya no tienes acceso a él.");

        if (!summary.IncludePhotos) query = query.Where(a => a.Type == AssetType.Video);
        var material = await query
            .Select(a => new
            {
                a.Id,
                a.Type,
                a.CapturedAt,
                a.FullPath,
                a.Duration,
                a.Checksum,
                a.IsFavorite,
                // The thumbnails are drawn upright (EXIF orientation, a phone's
                // rotation flag), so their shape is the shape as seen.
                ThumbnailRatio = a.Thumbnails
                    .Where(t => t.Size == ThumbnailSize.Small && t.Height > 0)
                    .Select(t => (double?)t.Width / t.Height)
                    .FirstOrDefault(),
                ExifWidth = a.Exif != null ? a.Exif.Width : null,
                ExifHeight = a.Exif != null ? a.Exif.Height : null,
                ExifOrientation = a.Exif != null ? a.Exif.Orientation : null,
            })
            .ToListAsync(ct);
        var filter = summary.Orientation;

        // What each video looks and sounds like, measured on an earlier summary.
        var videoIds = material.Where(m => m.Type == AssetType.Video).Select(m => m.Id).ToList();
        var analyses = await _db.AssetVideoAnalyses
            .Where(a => videoIds.Contains(a.AssetId))
            .ToDictionaryAsync(a => a.AssetId, ct);

        // Durations: a planner needs them and the indexer doesn't record them,
        // so probe what's missing and keep it on the asset for next time. A
        // video without a thumbnail yet gets probed for its shape too, and one
        // not measured yet (or changed since) gets measured.
        var candidates = new List<SummaryCandidate>(material.Count);
        var paths = new Dictionary<Guid, string>(material.Count);
        var videos = videoIds.Count;
        var seen = 0;
        foreach (var item in material)
        {
            ct.ThrowIfCancellationRequested();
            var path = await _settings.ResolvePhysicalPathAsync(item.FullPath);
            if (!File.Exists(path)) continue;

            if (item.Type != AssetType.Video)
            {
                var ratio = item.ThumbnailRatio
                    ?? SummaryOrientation.PhotoAspectRatio(item.ExifWidth, item.ExifHeight, item.ExifOrientation);
                if (ratio == null && filter != VideoSummaryOrientation.All)
                    ratio = await SummaryOrientation.ReadPhotoAspectRatioAsync(path, ct);
                if (!SummaryOrientation.Matches(filter, ratio)) continue;
                paths[item.Id] = path;
                candidates.Add(new SummaryCandidate(item.Id, false, item.CapturedAt, 0));
                continue;
            }

            var duration = item.Duration?.TotalSeconds;
            var shape = item.ThumbnailRatio;
            MediaProbe? probe = null;
            if (duration is not > 0 || (shape == null && filter != VideoSummaryOrientation.All))
            {
                probe = await FfmpegProcess.ProbeAsync(path, ct);
                shape ??= probe?.AspectRatio;
                duration = duration is > 0 ? duration : probe?.DurationSeconds ?? 0;
                if (duration > 0 && item.Duration is null)
                {
                    var span = TimeSpan.FromSeconds(duration.Value);
                    await _db.Assets.Where(a => a.Id == item.Id)
                        .ExecuteUpdateAsync(u => u.SetProperty(a => a.Duration, span), ct);
                }
            }
            if (!SummaryOrientation.Matches(filter, shape))
            {
                await SetProgressAsync(summary, AnalysisShare * ++seen / Math.Max(1, videos), ct);
                continue;
            }

            var signals = analyses.TryGetValue(item.Id, out var cached)
                && cached.Version == VideoAnalyzer.Version && cached.Checksum == item.Checksum
                ? ToSignals(cached)
                : await AnalyzeAsync(item.Id, item.Checksum, path, probe, cached, workDirectory, ct);
            await SetProgressAsync(summary, AnalysisShare * ++seen / Math.Max(1, videos), ct);

            paths[item.Id] = path;
            candidates.Add(new SummaryCandidate(item.Id, true, item.CapturedAt, duration.Value, item.IsFavorite, signals));
        }

        var plan = SummaryPlanner.Plan(candidates, summary.IncludePhotos,
            new SummaryPlanOptions { TargetSeconds = summary.DurationSeconds });
        if (plan.Count < 2)
            throw new VideoSummaryException(NotEnoughMessage(summary.IncludePhotos, filter));

        var photoIds = plan.Where(c => !c.IsVideo).Select(c => c.AssetId).ToList();
        var fallbacks = await _db.AssetThumbnails
            .Where(t => photoIds.Contains(t.AssetId) && t.Size == ThumbnailSize.Large)
            .ToDictionaryAsync(t => t.AssetId, t => t.FilePath, ct);
        var clips = plan
            .Select(c => new RenderClip(paths[c.AssetId], c.IsVideo, c.Start, c.Length, fallbacks.GetValueOrDefault(c.AssetId)))
            .ToList();

        // The summary sits in the timeline right after the last moment it shows.
        var capturedAt = plan.Max(c => c.CapturedAt).AddSeconds(1);
        var tz = await MetadataTimeZone.ResolveAsync(_settings, ct);
        var creationUtc = TimeZoneInfo.ConvertTimeToUtc(DateTime.SpecifyKind(capturedAt, DateTimeKind.Unspecified), tz);

        var rendered = Path.Combine(workDirectory, "summary.mp4");
        var (width, height) = SummaryOrientation.Frame(filter);
        var clipCount = await _renderer.RenderAsync(
            clips, new Frame(width, height), rendered, summary.Title, creationUtc, workDirectory,
            p => SetProgressAsync(summary, AnalysisShare + (0.95 - AnalysisShare) * p, ct),
            ct);
        summary.ClipCount = clipCount;

        return await StoreAsync(summary, rendered, capturedAt, ct);
    }

    /// <summary>
    /// Measures a video and keeps the result for the next summary. A video
    /// that can't be measured still goes in, its moment taken blind.
    /// </summary>
    private async Task<VideoSignals?> AnalyzeAsync(
        Guid assetId, string checksum, string path, MediaProbe? probe, AssetVideoAnalysis? stale,
        string workDirectory, CancellationToken ct)
    {
        VideoSignals? signals;
        try
        {
            probe ??= await FfmpegProcess.ProbeAsync(path, ct);
            signals = probe == null ? null : await VideoAnalyzer.AnalyzeAsync(path, probe, workDirectory, ct);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            _logger.LogWarning(ex, "Could not analyze video {AssetId} for a summary", assetId);
            return null;
        }
        if (signals == null) return null;

        var row = stale ?? new AssetVideoAnalysis { AssetId = assetId };
        row.Checksum = checksum;
        row.Version = VideoAnalyzer.Version;
        row.DurationSeconds = signals.DurationSeconds;
        row.Motion = signals.Motion;
        row.Brightness = signals.Brightness;
        row.Blur = signals.Blur;
        row.Loudness = signals.Loudness ?? [];
        row.SceneCuts = signals.SceneCuts;
        row.AnalyzedAt = DateTime.UtcNow;
        if (stale == null) _db.AssetVideoAnalyses.Add(row);
        await _db.SaveChangesAsync(ct);
        return signals;
    }

    private static VideoSignals ToSignals(AssetVideoAnalysis row) => new(
        row.DurationSeconds, row.Motion, row.Brightness, row.Blur,
        row.Loudness.Length > 0 ? row.Loudness : null, row.SceneCuts);

    /// <summary>
    /// Moves the rendered file into the user's library. Asking again for the
    /// same summary overwrites its previous video in place — same asset, so it
    /// keeps its albums, favourite flag and shares — and re-runs the enrichment
    /// that depends on the file's contents.
    /// </summary>
    private async Task<Asset> StoreAsync(VideoSummary summary, string rendered, DateTime capturedAt, CancellationToken ct)
    {
        var size = new FileInfo(rendered).Length;
        var previous = summary.AssetId is { } previousId
            ? await _db.Assets.FirstOrDefaultAsync(a => a.Id == previousId && a.DeletedAt == null, ct)
            : null;
        await CheckQuotaAsync(summary.OwnerId, size - (previous?.FileSize ?? 0), ct);

        if (previous != null)
        {
            var target = await _settings.ResolvePhysicalPathAsync(previous.FullPath);
            File.Move(rendered, target, overwrite: true);

            var file = new FileInfo(target);
            previous.Checksum = await _hashes.CalculateFileHashAsync(target, ct);
            previous.FileSize = file.Length;
            previous.FileModifiedAt = file.LastWriteTimeUtc;
            previous.CapturedAt = capturedAt;
            previous.Duration = null;
            previous.IsFileMissing = false;
            await _db.SaveChangesAsync(ct);

            await _enrichment.EnqueueAsync(previous.Id, AssetEnrichmentType.Exif, ct);
            await _enrichment.EnqueueAsync(previous.Id, AssetEnrichmentType.Thumbnails, ct);
            return previous;
        }

        var username = await _userStorage.GetUsernameAsync(summary.OwnerId, ct)
            ?? throw new VideoSummaryException("No se encontró la carpeta del usuario.");
        var folderVirtual = $"{UserStorageService.BuildVirtualRoot(username)}/{FolderName}";
        var folderPhysical = await _settings.ResolvePhysicalPathAsync(folderVirtual);
        Directory.CreateDirectory(folderPhysical);
        await UploadAssetsEndpoint.EnsureFolderRecordAsync(_db, summary.OwnerId, folderVirtual, ct);

        var destination = UniquePath(folderPhysical, FileNameFor(summary.Title));
        File.Move(rendered, destination);

        var asset = await _indexing.IndexFileAsync(destination, summary.OwnerId, ct)
            ?? throw new VideoSummaryException("No se pudo añadir el vídeo a la biblioteca.");

        // Seed the capture date now so the video lands in the right place
        // before EXIF runs; EXIF then reads the same instant back from the file.
        var tracked = await _db.Assets.FirstAsync(a => a.Id == asset.Id, ct);
        tracked.CapturedAt = capturedAt;
        await _db.SaveChangesAsync(ct);
        return tracked;
    }

    private async Task CheckQuotaAsync(Guid userId, long additionalBytes, CancellationToken ct)
    {
        var quota = await _db.Users.Where(u => u.Id == userId).Select(u => u.StorageQuotaBytes).FirstOrDefaultAsync(ct);
        if (quota is not { } limit || additionalBytes <= 0) return;

        var used = await _db.Assets
            .Where(a => a.OwnerId == userId && a.DeletedAt == null)
            .Where(StorageSpaces.CountsTowardsQuota)
            .SumAsync(a => (long?)a.FileSize, ct) ?? 0L;
        if (used + additionalBytes > limit)
            throw new VideoSummaryException("Has alcanzado el límite de almacenamiento asignado.");
    }

    private async Task SetProgressAsync(VideoSummary summary, double progress, CancellationToken ct)
    {
        // Progress is advisory; written straight to the row (the tracked entity
        // is saved for real at the end) and throttled to whole percents.
        var rounded = Math.Round(progress, 2);
        if (rounded <= summary.Progress) return;
        summary.Progress = rounded;
        try
        {
            await _db.VideoSummaries.Where(s => s.Id == summary.Id)
                .ExecuteUpdateAsync(u => u.SetProperty(s => s.Progress, rounded), ct);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            _logger.LogDebug(ex, "Could not record progress for summary {SummaryId}", summary.Id);
        }
    }

    /// <summary>Why there's nothing to cut, with what the user could change.</summary>
    internal static string NotEnoughMessage(bool includePhotos, VideoSummaryOrientation orientation)
    {
        var what = orientation switch
        {
            VideoSummaryOrientation.Landscape => includePhotos ? "vídeos ni fotos horizontales" : "vídeos horizontales",
            VideoSummaryOrientation.Portrait => includePhotos ? "vídeos ni fotos verticales" : "vídeos verticales",
            _ => includePhotos ? "vídeos ni fotos" : "vídeos",
        };
        var hint = !includePhotos ? " Prueba a incluir las fotos."
            : orientation != VideoSummaryOrientation.All ? " Prueba con todas las orientaciones."
            : "";
        return $"No hay suficientes {what} para montar un resumen.{hint}";
    }

    internal static string FileNameFor(string title)
    {
        var invalid = Path.GetInvalidFileNameChars().Concat(new[] { '/', '\\', ':', '*', '?', '"', '<', '>', '|' }).ToHashSet();
        var clean = new string(title.Select(c => invalid.Contains(c) || char.IsControl(c) ? ' ' : c).ToArray()).Trim();
        clean = string.Join(' ', clean.Split(' ', StringSplitOptions.RemoveEmptyEntries));
        if (clean.Length > 80) clean = clean[..80].TrimEnd();
        if (clean.Length == 0 || clean.Trim('.').Length == 0) clean = "Resumen";
        return clean + ".mp4";
    }

    private static string UniquePath(string directory, string fileName)
    {
        var path = Path.Combine(directory, fileName);
        var stem = Path.GetFileNameWithoutExtension(fileName);
        for (var n = 2; File.Exists(path); n++)
            path = Path.Combine(directory, $"{stem} ({n}).mp4");
        return path;
    }
}

/// <summary>A failure whose message is fit to show the user.</summary>
public sealed class VideoSummaryException(string message) : Exception(message);
