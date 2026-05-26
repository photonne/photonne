using System.Linq.Expressions;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Shared <see cref="IQueryable{Asset}"/> → <see cref="TimelineResponse"/>
/// projection. Replaces the previous Include(Exif).Include(Thumbnails).Include(Tags)
/// .Include(UserTags).ThenInclude(UserTag) cascade, which translates to a single
/// SQL query with an N×M×… cartesian join that explodes with even modest
/// thumbnail / tag fan-out.
///
/// Two reasons to keep this as a single shared expression:
///   * Used by both <see cref="TimelineEndpoint"/> and <see cref="RecentAssetsEndpoint"/>,
///     and any future endpoint that returns the same DTO must stay byte-for-byte
///     compatible with what the clients deserialize.
///   * Has to be expressible by EF Core's expression visitor — anything more
///     than property access, ternaries, and simple sub-queries silently falls
///     back to client-side evaluation.
/// </summary>
internal static class TimelineProjection
{
    public static readonly Expression<Func<Asset, TimelineResponse>> ToResponse = a => new TimelineResponse
    {
        Id = a.Id,
        FileName = a.FileName,
        FullPath = a.FullPath,
        FileSize = a.FileSize,
        FileCreatedAt = a.FileCreatedAt,
        FileModifiedAt = a.FileModifiedAt,
        Extension = a.Extension,
        ScannedAt = a.ScannedAt,
        Type = a.Type == AssetType.Video ? "Video" : "Image",
        Checksum = a.Checksum,
        HasExif = a.Exif != null,
        HasThumbnails = a.Thumbnails.Any(),
        SyncStatus = AssetSyncStatus.Synced,
        Width = a.Exif != null ? a.Exif.Width : null,
        Height = a.Exif != null ? a.Exif.Height : null,
        DeletedAt = a.DeletedAt,
        // Tag list: union of detected tag types + user-tag names. Built in SQL
        // via two sub-selects and combined client-side after materialization.
        // We project the two raw arrays here and stitch them in
        // <see cref="MergeTags"/>; doing string concat / Distinct in the
        // expression tree itself isn't reliably translatable.
        Tags = new List<string>(),
        IsFavorite = a.IsFavorite,
        IsArchived = a.IsArchived,
        IsFileMissing = a.IsFileMissing,
        DominantColor = a.Thumbnails
            .Where(t => t.Size == ThumbnailSize.Small)
            .Select(t => t.DominantColor)
            .FirstOrDefault(),
        IsReadOnly = a.ExternalLibraryId.HasValue
    };

    /// <summary>
    /// Side-channel projection used to fetch the tag lists for a page of assets
    /// in a single round-trip. Tags travel separately from the main projection
    /// so we don't pay for the join cardinality up front.
    /// </summary>
    public sealed record TagRow(Guid AssetId, string Label);

    public static List<string> MergeTags(IEnumerable<TagRow> rows) => rows
        .Select(r => r.Label)
        .Distinct(StringComparer.OrdinalIgnoreCase)
        .OrderBy(t => t)
        .ToList();
}
