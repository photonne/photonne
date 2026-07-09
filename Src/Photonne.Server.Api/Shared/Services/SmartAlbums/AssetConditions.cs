using System.Linq.Expressions;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.SmartAlbums;

/// <summary>
/// Single source of truth for the leaf <see cref="Asset"/> predicates that back
/// BOTH the search endpoint (via <see cref="AssetQueryBuilder"/>) and smart
/// albums (via <see cref="SmartRuleCompiler"/>). Every method returns an
/// EF-translatable <c>Expression&lt;Func&lt;Asset,bool&gt;&gt;</c> so the two
/// features filter identically (docs/smart-albums/). Combine them with
/// <see cref="PredicateBuilder"/>.
/// </summary>
public static class AssetConditions
{
    /// <summary>Free text over filename, path, caption, user-tag names, OCR
    /// text, and — when the term names an <see cref="AssetTagType"/> — that
    /// structural tag. Mirrors /api/assets/search's <c>q</c>.</summary>
    public static Expression<Func<Asset, bool>> Text(string q)
    {
        var tagTypeFilter = Enum.TryParse<AssetTagType>(q, ignoreCase: true, out var matched)
            ? (AssetTagType?)matched
            : null;

        return a =>
            a.FileName.Contains(q) ||
            a.FullPath.Contains(q) ||
            (a.Caption != null && a.Caption.Contains(q)) ||
            a.UserTags.Any(ut => ut.UserTag.Name.Contains(q)) ||
            a.RecognizedTextLines.Any(t => EF.Functions.ILike(t.Text, "%" + q + "%")) ||
            (tagTypeFilter.HasValue && a.Tags.Any(t => t.TagType == tagTypeFilter.Value));
    }

    /// <summary>
    /// Capture-date range on <see cref="Asset.CapturedAt"/>. <paramref name="to"/>
    /// includes the whole selected day. Returns <see cref="PredicateBuilder.True{T}"/>
    /// when both bounds are null. Matches the search endpoint's UTC handling.
    /// </summary>
    public static Expression<Func<Asset, bool>> CapturedBetween(DateTime? from, DateTime? to)
    {
        var predicate = PredicateBuilder.True<Asset>();
        if (from.HasValue)
        {
            var fromUtc = from.Value.ToUniversalTime();
            predicate = predicate.And(a => a.CapturedAt >= fromUtc);
        }
        if (to.HasValue)
        {
            var toUtc = to.Value.ToUniversalTime().Date.AddDays(1);
            predicate = predicate.And(a => a.CapturedAt < toUtc);
        }
        return predicate;
    }

    /// <summary>Substring match on <see cref="Asset.FullPath"/> (search's <c>folder</c>).</summary>
    public static Expression<Func<Asset, bool>> FullPathContains(string folder) =>
        a => a.FullPath.Contains(folder);

    /// <summary>Asset lives directly in one of the given folders (already
    /// subtree-expanded by the caller for smart albums).</summary>
    public static Expression<Func<Asset, bool>> FolderIn(IReadOnlySet<Guid> folderIds) =>
        a => a.FolderId.HasValue && folderIds.Contains(a.FolderId.Value);

    /// <summary>Has at least one detected object with the (case-insensitive) label.</summary>
    public static Expression<Func<Asset, bool>> HasObject(string label)
    {
        var normalized = label.Trim().ToLowerInvariant();
        return a => a.DetectedObjects.Any(o => o.Label.ToLower() == normalized);
    }

    /// <summary>Has at least one scene classification with the (case-insensitive) label.</summary>
    public static Expression<Func<Asset, bool>> HasScene(string label)
    {
        var normalized = label.Trim().ToLowerInvariant();
        return a => a.ClassifiedScenes.Any(s => s.Label.ToLower() == normalized);
    }

    /// <summary>Has a manual user tag with the given id.</summary>
    public static Expression<Func<Asset, bool>> HasUserTag(Guid userTagId) =>
        a => a.UserTags.Any(ut => ut.UserTagId == userTagId);

    /// <summary>Has the given structural tag type.</summary>
    public static Expression<Func<Asset, bool>> HasTagType(AssetTagType tagType) =>
        a => a.Tags.Any(t => t.TagType == tagType);

    public static Expression<Func<Asset, bool>> IsFavorite(bool value) =>
        a => a.IsFavorite == value;

    public static Expression<Func<Asset, bool>> OfType(AssetType type) =>
        a => a.Type == type;

    /// <summary>OCR full-text match (websearch tsquery, 'simple' config).</summary>
    public static Expression<Func<Asset, bool>> Ocr(string raw)
    {
        var query = raw.Trim();
        return a => a.RecognizedTextLines.Any(t =>
            EF.Functions.ToTsVector("simple", t.Text)
                .Matches(EF.Functions.WebSearchToTsQuery("simple", query)));
    }

    /// <summary>
    /// Asset has a non-rejected face that <paramref name="identityUserId"/> has
    /// linked to <paramref name="personId"/>. Identity is per-user
    /// (<see cref="UserFaceAssignment"/>); the legacy <see cref="Face"/> identity
    /// columns are no longer authoritative.
    /// </summary>
    public static Expression<Func<Asset, bool>> HasPerson(
        ApplicationDbContext db, Guid identityUserId, Guid personId) =>
        a => a.Faces.Any(f => db.UserFaceAssignments.Any(uf =>
            uf.FaceId == f.Id
            && uf.UserId == identityUserId
            && uf.PersonId == personId
            && !uf.IsRejected));

    /// <summary>
    /// Asset has a non-rejected face that <paramref name="identityUserId"/> has
    /// linked to ANY of <paramref name="personIds"/> (people condition with
    /// match=any — the smart-album default, see docs/smart-albums/creation-ux.md).
    /// </summary>
    public static Expression<Func<Asset, bool>> HasAnyPerson(
        ApplicationDbContext db, Guid identityUserId, IReadOnlyList<Guid> personIds) =>
        a => a.Faces.Any(f => db.UserFaceAssignments.Any(uf =>
            uf.FaceId == f.Id
            && uf.UserId == identityUserId
            && uf.PersonId.HasValue
            && personIds.Contains(uf.PersonId.Value)
            && !uf.IsRejected));
}
