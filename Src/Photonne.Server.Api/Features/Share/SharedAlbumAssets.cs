using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Share;

/// <summary>
/// The photos a public link to an album shows. A manual album is its
/// AlbumAssets rows, in the album's order; a smart album keeps none and is
/// resolved live from its rule, as its owner sees it, newest first. Trashed
/// photos are never part of a shared album.
/// </summary>
public static class SharedAlbumAssets
{
    private static readonly JsonSerializerOptions RuleJson = new(JsonSerializerDefaults.Web);

    public static async Task<IQueryable<Asset>> QueryAsync(
        ApplicationDbContext db, SmartAlbumResolver smartResolver, Album album, CancellationToken ct)
    {
        if (album.Kind == AlbumKind.Smart)
        {
            var rule = album.SmartRule is null
                ? null
                : JsonSerializer.Deserialize<SmartRuleNode>(album.SmartRule, RuleJson);
            if (rule is null) return db.Assets.Where(_ => false);

            var resolved = await smartResolver.ResolveAsync(rule, album.OwnerId, album.OwnerId, ct);
            return resolved
                .OrderByDescending(a => a.CapturedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .ThenBy(a => a.Id);
        }

        return db.AlbumAssets
            .AsNoTracking()
            .Where(aa => aa.AlbumId == album.Id && aa.Asset.DeletedAt == null)
            .OrderBy(aa => aa.Order).ThenBy(aa => aa.AddedAt)
            .Select(aa => aa.Asset);
    }

    /// <summary>Whether the link's album shows this photo (the media endpoints' gate).</summary>
    public static async Task<bool> ContainsAsync(
        ApplicationDbContext db, SmartAlbumResolver smartResolver, Album album, Guid assetId, CancellationToken ct)
    {
        var assets = await QueryAsync(db, smartResolver, album, ct);
        return await assets.AnyAsync(a => a.Id == assetId, ct);
    }
}
