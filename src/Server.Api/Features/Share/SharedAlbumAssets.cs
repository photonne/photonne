using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Share;

/// <summary>
/// The photos a public link to an album shows: its AlbumAssets rows. A manual
/// album lists them in its own order; a smart album's rows are its rule's
/// materialized result (as its owner sees it), newest first. Trashed photos
/// are never part of a shared album.
/// </summary>
public static class SharedAlbumAssets
{
    public static IQueryable<Asset> Query(ApplicationDbContext db, Album album)
    {
        var rows = db.AlbumAssets
            .AsNoTracking()
            .Where(aa => aa.AlbumId == album.Id && aa.Asset.DeletedAt == null);

        if (album.Kind == AlbumKind.Smart)
        {
            return rows
                .Select(aa => aa.Asset)
                .Where(a => !a.IsArchived && !a.IsFileMissing)
                .OrderByDescending(a => a.CapturedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .ThenBy(a => a.Id);
        }

        return rows
            .OrderBy(aa => aa.Order).ThenBy(aa => aa.AddedAt)
            .Select(aa => aa.Asset);
    }

    /// <summary>Whether the link's album shows this photo (the media endpoints' gate).</summary>
    public static Task<bool> ContainsAsync(ApplicationDbContext db, Album album, Guid assetId, CancellationToken ct) =>
        Query(db, album).AnyAsync(a => a.Id == assetId, ct);
}
