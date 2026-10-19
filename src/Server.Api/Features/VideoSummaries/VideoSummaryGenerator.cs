using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.UploadAssets;
using Photonne.Server.Api.Features.VideoSummaries.Highlights;
using Photonne.Server.Api.Features.VideoSummaries.Music;
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

    /// <summary>Seconds each clip blends into the next.</summary>
    public const double Crossfade = 0.5;

    private readonly ApplicationDbContext _db;
    private readonly VideoSummarySources _sources;
    private readonly SettingsService _settings;
    private readonly UserStorageService _userStorage;
    private readonly AssetIndexingService _indexing;
    private readonly IEnrichmentService _enrichment;
    private readonly FileHashService _hashes;
    private readonly SummaryRenderer _renderer;
    private readonly VideoProfileCache _profiles;
    private readonly IVideoMlService _ml;
    private readonly VideoMlAnalyzer _mlAnalyzer;
    private readonly SummaryMusicStore _music;
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
        VideoProfileCache profiles,
        IVideoMlService ml,
        VideoMlAnalyzer mlAnalyzer,
        SummaryMusicStore music,
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
        _profiles = profiles;
        _ml = ml;
        _mlAnalyzer = mlAnalyzer;
        _music = music;
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

        // Durations: a planner needs them and the indexer doesn't record them,
        // so probe what's missing and keep it on the asset for next time. A
        // video without a thumbnail yet gets probed for its shape too.
        var candidates = new List<SummaryCandidate>(material.Count);
        var paths = new Dictionary<Guid, string>(material.Count);
        var videos = material.Count(m => m.Type == AssetType.Video);
        var probed = 0;
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
                candidates.Add(new SummaryCandidate(item.Id, false, item.CapturedAt, 0, item.IsFavorite));
                continue;
            }

            var duration = item.Duration?.TotalSeconds;
            var shape = item.ThumbnailRatio;
            if (duration is not > 0 || (shape == null && filter != VideoSummaryOrientation.All))
            {
                var probe = await FfmpegProcess.ProbeAsync(path, ct);
                shape ??= probe?.AspectRatio;
                duration = duration is > 0 ? duration : probe?.DurationSeconds ?? 0;
                if (duration > 0 && item.Duration is null)
                {
                    var span = TimeSpan.FromSeconds(duration.Value);
                    await _db.Assets.Where(a => a.Id == item.Id)
                        .ExecuteUpdateAsync(u => u.SetProperty(a => a.Duration, span), ct);
                }
                await SetProgressAsync(summary, 0.1 * ++probed / Math.Max(1, videos), ct);
            }
            if (!SummaryOrientation.Matches(filter, shape)) continue;
            paths[item.Id] = path;
            candidates.Add(new SummaryCandidate(item.Id, true, item.CapturedAt, duration.Value, item.IsFavorite));
        }

        var planOptions = new SummaryPlanOptions { TargetSeconds = summary.DurationSeconds };
        if (summary.IncludePhotos)
            candidates = await WithPhotoEmbeddingsAsync(candidates, ct);

        // Analyse a shortlist (cached per file) so the cut picks moments
        // instead of a fixed stretch of each video: measurements first, then
        // — when the ML service is there — faces, looks and speech.
        var checksums = material.ToDictionary(m => m.Id, m => m.Checksum);
        var toAnalyze = SummaryPlanner.VideosToAnalyze(candidates, summary.IncludePhotos, planOptions);
        var capabilities = toAnalyze.Count > 0 ? await _ml.CapabilitiesAsync(ct) : MlCapabilities.None;
        var people = capabilities.Faces ? await PeopleIndex.LoadAsync(_db, summary.OwnerId, ct) : PeopleIndex.Empty;

        var analysed = new Dictionary<Guid, AnalysedVideo>();
        for (var i = 0; i < toAnalyze.Count; i++)
        {
            var video = toAnalyze[i];
            var profile = await _profiles.GetAsync(
                video.AssetId, checksums[video.AssetId], paths[video.AssetId],
                Path.Combine(workDirectory, "analysis"), ct);
            await SetProgressAsync(summary, 0.1 + 0.15 * (i + 1) / toAnalyze.Count, ct);
            if (profile == null) continue;

            var ml = await _profiles.GetMlAsync(
                video.AssetId, checksums[video.AssetId], paths[video.AssetId], profile.Duration,
                capabilities, _mlAnalyzer, ct);
            analysed[video.AssetId] = new AnalysedVideo(
                profile,
                MomentSignals.Build(ml, profile.Seconds, people),
                ml?.Embedding is { } embedding ? Vectors.Decode(embedding) : null);
            await SetProgressAsync(summary, 0.25 + 0.2 * (i + 1) / toAnalyze.Count, ct);
        }

        var plan = SummaryPlanner.Plan(candidates, summary.IncludePhotos, planOptions, analysed);
        if (plan.Count < 2)
            throw new VideoSummaryException(NotEnoughMessage(summary.IncludePhotos, filter));

        // Finishing: clips blend into each other, cut on the music's beat when
        // there is music, with a title card and a caption at each chapter.
        var style = new RenderStyle { Crossfade = Crossfade };
        var font = summary.ShowTitles ? FontLocator.Find() : null;
        string? musicPath = null;
        if (summary.MusicId is { } musicId)
        {
            musicPath = _music.Find(summary.OwnerId, musicId);
            if (musicPath == null)
                _logger.LogWarning("Music {MusicId} of summary {SummaryId} is gone; rendering without it", musicId, summary.Id);
        }
        if (musicPath != null)
        {
            var titleOffset = font != null ? SummaryRenderer.TitleSeconds - Crossfade : 0;
            var beats = await BeatTracker.TrackAsync(
                musicPath, plan.Sum(c => c.Length) + titleOffset + 30, Path.Combine(workDirectory, "music"), ct);
            if (beats != null)
            {
                var durations = candidates.ToDictionary(c => c.AssetId, c => c.DurationSeconds);
                var available = plan.Select(c => c.IsVideo ? durations[c.AssetId] - c.Start : 8).ToList();
                plan = BeatSync.Align(plan, available, beats, Crossfade, titleOffset);
            }
        }

        var planIds = plan.Select(c => c.AssetId).Distinct().ToList();
        var places = font == null
            ? new Dictionary<Guid, string>()
            : await _db.AssetExifs
                .AsNoTracking()
                .Where(e => planIds.Contains(e.AssetId) && e.PlaceId != null)
                .Join(_db.Places, e => e.PlaceId, p => p.Id, (e, p) => new { e.AssetId, p.Name })
                .ToDictionaryAsync(x => x.AssetId, x => x.Name, ct);
        var text = SummaryChapters.Describe(plan, summary.Source, places);
        style = style with
        {
            FontFile = font,
            Title = font != null ? summary.Title : null,
            Subtitle = text.Subtitle,
            MusicPath = musicPath,
        };

        var photoIds = plan.Where(c => !c.IsVideo).Select(c => c.AssetId).ToList();
        var fallbacks = await _db.AssetThumbnails
            .Where(t => photoIds.Contains(t.AssetId) && t.Size == ThumbnailSize.Large)
            .ToDictionaryAsync(t => t.AssetId, t => t.FilePath, ct);
        var clips = plan
            .Select((c, i) => new RenderClip(
                paths[c.AssetId], c.IsVideo, c.Start, c.Length, fallbacks.GetValueOrDefault(c.AssetId),
                font != null ? text.Captions[i] : null))
            .ToList();

        // The summary sits in the timeline right after the last moment it shows.
        var capturedAt = plan.Max(c => c.CapturedAt).AddSeconds(1);
        var tz = await MetadataTimeZone.ResolveAsync(_settings, ct);
        var creationUtc = TimeZoneInfo.ConvertTimeToUtc(DateTime.SpecifyKind(capturedAt, DateTimeKind.Unspecified), tz);

        var rendered = Path.Combine(workDirectory, "summary.mp4");
        var (width, height) = SummaryOrientation.Frame(filter);
        var clipCount = await _renderer.RenderAsync(
            clips, new Frame(width, height), rendered, summary.Title, creationUtc, workDirectory,
            p => SetProgressAsync(summary, 0.45 + 0.5 * p, ct),
            ct,
            style);
        summary.ClipCount = clipCount;

        return await StoreAsync(summary, rendered, capturedAt, ct);
    }

    /// <summary>The photos' CLIP embeddings, where the enrichment already made
    /// them, so a burst goes in as one photo.</summary>
    private async Task<List<SummaryCandidate>> WithPhotoEmbeddingsAsync(List<SummaryCandidate> candidates, CancellationToken ct)
    {
        var photoIds = candidates.Where(c => !c.IsVideo).Select(c => c.AssetId).ToList();
        if (photoIds.Count == 0) return candidates;
        var embeddings = await _db.AssetEmbeddings
            .AsNoTracking()
            .Where(e => photoIds.Contains(e.AssetId))
            .Select(e => new { e.AssetId, e.Embedding })
            .ToDictionaryAsync(e => e.AssetId, e => Vectors.Normalize(e.Embedding.ToArray()), ct);
        return candidates
            .Select(c => !c.IsVideo && embeddings.TryGetValue(c.AssetId, out var e) ? c with { Embedding = e } : c)
            .ToList();
    }

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
