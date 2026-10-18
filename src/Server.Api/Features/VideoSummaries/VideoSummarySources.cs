using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.VideoSummaries;

/// <summary>
/// The assets a summary may draw from, for a given requester. Visibility is
/// the same as wherever the user would browse that material themselves:
///   * a year is the timeline's set (not archived, not a Live Photo's clip,
///     discoverable to the user) captured in that calendar year;
///   * an album is what the album shows the requester — its members for a
///     manual album, the live rule (owner-anchored, intersected with the
///     requester's own visibility) for a smart one.
/// Generated summaries are never material for another summary.
/// </summary>
public sealed class VideoSummarySources
{
    private static readonly JsonSerializerOptions SmartRuleJson = new(JsonSerializerDefaults.Web);

    private readonly ApplicationDbContext _db;
    private readonly AssetVisibilityService _visibility;
    private readonly SmartAlbumResolver _smartResolver;

    public VideoSummarySources(ApplicationDbContext db, AssetVisibilityService visibility, SmartAlbumResolver smartResolver)
    {
        _db = db;
        _visibility = visibility;
        _smartResolver = smartResolver;
    }

    public async Task<IQueryable<Asset>> ForYearAsync(Guid userId, int year, CancellationToken ct)
    {
        var scope = await _visibility.GetScopeAsync(userId, ct);
        var from = new DateTime(year, 1, 1);
        var to = from.AddYears(1);

        var query = _db.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                     && !a.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart))
            .Where(scope.DiscoveryPredicate())
            .Where(a => a.CapturedAt >= from && a.CapturedAt < to);
        return Material(query);
    }

    /// <summary>Null when the album doesn't exist or the user can't open it.</summary>
    public async Task<IQueryable<Asset>?> ForAlbumAsync(Guid userId, Guid albumId, CancellationToken ct)
    {
        var album = await GetReadableAlbumAsync(userId, albumId, ct);
        if (album == null) return null;

        if (album.Kind == AlbumKind.Smart && album.ResolveMode == SmartResolveMode.Dynamic && album.SmartRule != null)
        {
            var rule = JsonSerializer.Deserialize<SmartRuleNode>(album.SmartRule, SmartRuleJson);
            if (rule == null) return Material(_db.Assets.Where(_ => false));
            return Material(await _smartResolver.ResolveAsync(rule, album.OwnerId, userId, ct));
        }

        // Manual albums (and materialized smart ones) show their members to
        // anyone who can open the album — the same set GET /albums/{id}/assets returns.
        var members = _db.AlbumAssets
            .Where(aa => aa.AlbumId == albumId)
            .Select(aa => aa.Asset)
            .Where(a => a.DeletedAt == null && !a.IsFileMissing);
        return Material(members);
    }

    /// <summary>The album when <paramref name="userId"/> owns it or has read access.</summary>
    public Task<Album?> GetReadableAlbumAsync(Guid userId, Guid albumId, CancellationToken ct) =>
        _db.Albums
            .AsNoTracking()
            .Where(a => a.Id == albumId)
            .Where(a => a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanRead))
            .FirstOrDefaultAsync(ct);

    private IQueryable<Asset> Material(IQueryable<Asset> query) =>
        query.Where(a => (a.Type == AssetType.Video || a.Type == AssetType.Image)
                      && !_db.VideoSummaries.Any(s => s.AssetId == a.Id));
}
