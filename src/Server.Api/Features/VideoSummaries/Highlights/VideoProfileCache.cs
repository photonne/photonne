using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// Video profiles, analysed once per file version: read from
/// <see cref="AssetVideoAnalysis"/> when the checksum and analyzer version
/// still match, otherwise measured with <see cref="VideoAnalyzer"/> and stored.
/// </summary>
public sealed class VideoProfileCache
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private readonly ApplicationDbContext _db;
    private readonly ILogger<VideoProfileCache> _logger;

    public VideoProfileCache(ApplicationDbContext db, ILogger<VideoProfileCache> logger)
    {
        _db = db;
        _logger = logger;
    }

    /// <summary>The profile, or null when the video can't be analysed (the
    /// summary then cuts that one blind).</summary>
    public async Task<VideoProfile?> GetAsync(
        Guid assetId, string checksum, string path, string workDirectory, CancellationToken ct)
    {
        var cached = await _db.AssetVideoAnalyses.AsNoTracking()
            .FirstOrDefaultAsync(a => a.AssetId == assetId, ct);
        if (cached is { AnalyzerVersion: VideoAnalyzer.Version } && cached.Checksum == checksum)
        {
            try
            {
                var profile = JsonSerializer.Deserialize<VideoProfile>(cached.Profile, Json);
                if (profile != null) return profile;
            }
            catch (JsonException)
            {
                // Unreadable: analyse again below.
            }
        }

        VideoProfile? analysed;
        try
        {
            analysed = await VideoAnalyzer.AnalyzeAsync(path, workDirectory, ct);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            _logger.LogWarning(ex, "Could not analyse video {AssetId}", assetId);
            return null;
        }
        if (analysed == null) return null;

        var json = JsonSerializer.Serialize(analysed, Json);
        var updated = await _db.AssetVideoAnalyses
            .Where(a => a.AssetId == assetId)
            .ExecuteUpdateAsync(u => u
                .SetProperty(a => a.Checksum, checksum)
                .SetProperty(a => a.AnalyzerVersion, VideoAnalyzer.Version)
                .SetProperty(a => a.Profile, json)
                .SetProperty(a => a.AnalyzedAt, DateTime.UtcNow), ct);
        if (updated == 0)
        {
            _db.AssetVideoAnalyses.Add(new AssetVideoAnalysis
            {
                AssetId = assetId,
                Checksum = checksum,
                AnalyzerVersion = VideoAnalyzer.Version,
                Profile = json,
            });
            try
            {
                await _db.SaveChangesAsync(ct);
            }
            catch (DbUpdateException)
            {
                // Another summary stored it first; ours is just as good.
                _db.ChangeTracker.Clear();
            }
        }
        return analysed;
    }

    /// <summary>
    /// What the ML service makes of the video: the cached part, completed
    /// with whatever capability is available now and wasn't then. Null when
    /// there's nothing (no ML, or it failed) — the summary does without.
    /// </summary>
    public async Task<VideoMlProfile?> GetMlAsync(
        Guid assetId, string checksum, string path, double duration,
        MlCapabilities capabilities, VideoMlAnalyzer analyzer, CancellationToken ct)
    {
        var row = await _db.AssetVideoAnalyses.AsNoTracking()
            .Where(a => a.AssetId == assetId && a.Checksum == checksum)
            .Select(a => new { a.MlProfile, a.MlVersion })
            .FirstOrDefaultAsync(ct);

        VideoMlProfile? cached = null;
        if (row is { MlProfile: not null } && row.MlVersion == VideoMlProfile.Version)
        {
            try { cached = JsonSerializer.Deserialize<VideoMlProfile>(row.MlProfile, Json); }
            catch (JsonException) { /* analyse again */ }
        }
        if (!capabilities.Any || cached is { IsComplete: true }) return cached;

        VideoMlProfile? analysed;
        try
        {
            analysed = await analyzer.AnalyzeAsync(assetId, path, duration, capabilities, cached, ct);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            _logger.LogWarning(ex, "ML analysis of video {AssetId} failed", assetId);
            return cached;
        }
        if (analysed == null || ReferenceEquals(analysed, cached) || row == null) return analysed;

        var json = JsonSerializer.Serialize(analysed, Json);
        await _db.AssetVideoAnalyses
            .Where(a => a.AssetId == assetId && a.Checksum == checksum)
            .ExecuteUpdateAsync(u => u
                .SetProperty(a => a.MlProfile, json)
                .SetProperty(a => a.MlVersion, VideoMlProfile.Version), ct);
        return analysed;
    }
}
