using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.VideoSummaries.Music;

/// <summary>A music file the user uploaded for a summary.</summary>
public sealed record SummaryMusic(Guid Id, string Name, double DurationSeconds);

/// <summary>
/// Background music for video summaries. Photonne doesn't index audio, so the
/// user uploads a track with the request; it's kept (per user, in the
/// thumbnails volume — never in the library, where the indexer would pick it
/// up) so asking for the summary again reuses it. Uploads no summary ended up
/// using are swept after a while.
/// </summary>
public sealed class SummaryMusicStore
{
    public const long MaxBytes = 50L * 1024 * 1024;
    public const double MinSeconds = 10;

    private static readonly HashSet<string> Extensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".mp3", ".m4a", ".aac", ".wav", ".ogg", ".oga", ".opus", ".flac",
    };

    private static readonly TimeSpan Unused = TimeSpan.FromDays(2);

    private readonly string _root;
    private readonly ILogger<SummaryMusicStore> _logger;

    public SummaryMusicStore(IConfiguration configuration, ILogger<SummaryMusicStore> logger)
    {
        _root = Path.Combine(configuration["ThumbnailsPath"] ?? "/data/thumbnails", ThumbnailFolders.VideoSummaries, "music");
        _logger = logger;
    }

    /// <summary>Stores an upload; throws <see cref="VideoSummaryException"/>
    /// with a message for the user when it isn't usable music.</summary>
    public async Task<SummaryMusic> SaveAsync(Guid userId, IFormFile file, CancellationToken ct)
    {
        var extension = Path.GetExtension(file.FileName);
        if (!Extensions.Contains(extension))
            throw new VideoSummaryException("Formato de música no admitido. Usa MP3, M4A, AAC, WAV, OGG, Opus o FLAC.");
        if (file.Length == 0 || file.Length > MaxBytes)
            throw new VideoSummaryException("La música debe ocupar como mucho 50 MB.");

        var id = Guid.NewGuid();
        var directory = Path.Combine(_root, userId.ToString("N"));
        Directory.CreateDirectory(directory);
        var path = Path.Combine(directory, id.ToString("N") + extension.ToLowerInvariant());
        await using (var stream = File.Create(path))
            await file.CopyToAsync(stream, ct);

        var probe = await FfmpegProcess.ProbeAsync(path, ct);
        if (probe is not { HasAudio: true } || probe.DurationSeconds < MinSeconds)
        {
            File.Delete(path);
            throw new VideoSummaryException("El archivo no es una pista de música válida (al menos 10 segundos de audio).");
        }

        var name = Path.GetFileNameWithoutExtension(file.FileName).Trim();
        if (name.Length > 200) name = name[..200];
        return new SummaryMusic(id, name.Length > 0 ? name : "Música", Math.Round(probe.DurationSeconds, 1));
    }

    /// <summary>The stored file for this user's upload, or null.</summary>
    public string? Find(Guid userId, Guid musicId)
    {
        var directory = Path.Combine(_root, userId.ToString("N"));
        if (!Directory.Exists(directory)) return null;
        return Directory.EnumerateFiles(directory, musicId.ToString("N") + ".*").FirstOrDefault();
    }

    /// <summary>Deletes uploads older than a couple of days that no summary uses.</summary>
    public async Task SweepAsync(ApplicationDbContext db, CancellationToken ct)
    {
        if (!Directory.Exists(_root)) return;
        var used = (await db.VideoSummaries
                .Where(s => s.MusicId != null)
                .Select(s => s.MusicId!.Value)
                .ToListAsync(ct))
            .Select(id => id.ToString("N"))
            .ToHashSet();

        foreach (var file in Directory.EnumerateFiles(_root, "*", SearchOption.AllDirectories))
        {
            if (used.Contains(Path.GetFileNameWithoutExtension(file))) continue;
            if (DateTime.UtcNow - File.GetLastWriteTimeUtc(file) < Unused) continue;
            try { File.Delete(file); }
            catch (IOException ex) { _logger.LogDebug(ex, "Could not sweep summary music {File}", file); }
        }
    }
}
