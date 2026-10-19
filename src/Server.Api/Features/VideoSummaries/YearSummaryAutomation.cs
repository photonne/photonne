using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.VideoSummaries.Music;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.VideoSummaries;

/// <summary>
/// The summary of the year, without asking: for the users who turned it on
/// (<see cref="SettingKey"/>), the nightly run asks for it at the end of
/// December, as if they had pressed the button — with the options they used
/// last time for that year, or the defaults. Late enough to catch Christmas;
/// a server that was off then catches up in the first half of January.
///
/// Never twice: a summary of that year asked for since December 1 (by hand or
/// by an earlier night) counts as this year's.
/// </summary>
public sealed class YearSummaryAutomation
{
    /// <summary>Per-user setting, "true" to have it made every year.</summary>
    public const string SettingKey = "VideoSummarySettings.AutoYear";

    private readonly IServiceScopeFactory _scopes;
    private readonly ILogger<YearSummaryAutomation> _logger;

    public YearSummaryAutomation(IServiceScopeFactory scopes, ILogger<YearSummaryAutomation> logger)
    {
        _scopes = scopes;
        _logger = logger;
    }

    /// <summary>The year whose summary is due on <paramref name="localToday"/>, if any.</summary>
    internal static int? DueYear(DateTime localToday) => localToday switch
    {
        { Month: 12, Day: >= 26 } => localToday.Year,
        { Month: 1, Day: <= 15 } => localToday.Year - 1,
        _ => null,
    };

    /// <summary>Asks for the due summaries; returns how many it queued.</summary>
    public async Task<int> RequestDueAsync(DateTime localToday, CancellationToken ct)
    {
        if (DueYear(localToday) is not { } year) return 0;

        List<Guid> userIds;
        using (var scope = _scopes.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
            userIds = await db.Settings
                .Where(s => s.Key == SettingKey && s.Value == "true" && s.OwnerId != Guid.Empty)
                .Join(db.Users.Where(u => u.IsActive), s => s.OwnerId, u => u.Id, (s, u) => u.Id)
                .ToListAsync(ct);
        }

        var queued = 0;
        foreach (var userId in userIds)
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                // A scope per user: the visibility service behind the sources
                // caches one user's permissions.
                using var scope = _scopes.CreateScope();
                if (await RequestAsync(scope.ServiceProvider, userId, year, ct)) queued++;
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                _logger.LogWarning(ex, "Could not request the {Year} summary of user {UserId}", year, userId);
            }
        }
        return queued;
    }

    private static async Task<bool> RequestAsync(IServiceProvider services, Guid userId, int year, CancellationToken ct)
    {
        var db = services.GetRequiredService<ApplicationDbContext>();
        var sourceKey = $"year:{year}";
        var summary = await db.VideoSummaries
            .FirstOrDefaultAsync(s => s.OwnerId == userId && s.SourceKey == sourceKey, ct);

        var seasonStart = new DateTime(year, 12, 1, 0, 0, 0, DateTimeKind.Utc);
        if (summary is { Status: VideoSummaryStatus.Pending or VideoSummaryStatus.Running or VideoSummaryStatus.Planned })
            return false;
        if (summary != null && summary.RequestedAt >= seasonStart) return false;

        var active = await db.VideoSummaries.CountAsync(s => s.OwnerId == userId
            && (s.Status == VideoSummaryStatus.Pending || s.Status == VideoSummaryStatus.Running), ct);
        if (active >= VideoSummariesEndpoint.MaxQueuedPerUser) return false;

        var includePhotos = summary?.IncludePhotos ?? false;
        var material = await services.GetRequiredService<VideoSummarySources>().ForYearAsync(userId, year, ct);
        var usable = await material.CountAsync(a => a.Type == AssetType.Video || (includePhotos && a.Type == AssetType.Image), ct);
        if (usable < 2) return false;

        if (summary == null)
        {
            summary = new VideoSummary
            {
                OwnerId = userId,
                Source = VideoSummarySource.Year,
                SourceKey = sourceKey,
                Year = year,
            };
            db.VideoSummaries.Add(summary);
        }
        else if (summary.MusicId is { } musicId
                 && services.GetRequiredService<SummaryMusicStore>().Find(userId, musicId) == null)
        {
            // The track from last time is gone: the rest of the options still hold.
            summary.MusicId = null;
            summary.MusicName = null;
        }

        summary.Title = $"Resumen {year}";
        summary.Status = VideoSummaryStatus.Pending;
        summary.PlanApproved = false;
        summary.Progress = 0;
        summary.Error = null;
        summary.RequestedAt = DateTime.UtcNow;
        summary.CompletedAt = null;
        await db.SaveChangesAsync(ct);

        services.GetRequiredService<VideoSummaryQueue>().Enqueue(summary.Id);
        return true;
    }
}
