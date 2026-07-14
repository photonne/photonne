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
    ///
    /// NOTE: the ToUniversalTime() calls below shift a Kind.Unspecified bound by
    /// the SERVER's offset, while CapturedAt holds the photo's own naive local
    /// wall-clock — so this drifts on any host whose TZ isn't UTC. Kept for the
    /// search callers that already pass bounds in that frame; new code that
    /// reasons about a local calendar wants <see cref="CapturedBetweenLocal"/>.
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

    /// <summary>
    /// Capture-date range compared in the SAME frame the column is stored in:
    /// <see cref="Asset.CapturedAt"/> is the photo's own naive local wall-clock,
    /// so the bounds must be wall-clock too. <paramref name="to"/> includes the
    /// whole selected day.
    ///
    /// Use this — not <see cref="CapturedBetween"/> — for anything that reasons
    /// about a local calendar (memories, "favourites of 2023"). CapturedBetween
    /// runs the bounds through ToUniversalTime(), which reads a Kind.Unspecified
    /// bound as *server* local time and shifts the window by the host's offset.
    /// It is preserved as-is because the search endpoint's callers pass bounds in
    /// that frame already.
    /// </summary>
    public static Expression<Func<Asset, bool>> CapturedBetweenLocal(DateTime? from, DateTime? to)
    {
        var predicate = PredicateBuilder.True<Asset>();
        if (from.HasValue)
        {
            var fromLocal = from.Value;
            predicate = predicate.And(a => a.CapturedAt >= fromLocal);
        }
        if (to.HasValue)
        {
            var toLocal = to.Value.Date.AddDays(1);
            predicate = predicate.And(a => a.CapturedAt < toLocal);
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

    /// <summary>
    /// Has any of <paramref name="labels"/> as a detected object, scoring at least
    /// <paramref name="minConfidence"/>.
    /// </summary>
    public static Expression<Func<Asset, bool>> HasAnyObjectAbove(
        IReadOnlyList<string> labels, float minConfidence)
    {
        var normalized = labels.Select(l => l.Trim().ToLowerInvariant()).ToList();
        return a => a.DetectedObjects.Any(o =>
            normalized.Contains(o.Label.ToLower()) && o.Confidence >= minConfidence);
    }

    /// <summary>
    /// Has any of <paramref name="labels"/> as a classified scene, scoring at least
    /// <paramref name="minConfidence"/>.
    ///
    /// The confidence floor is the whole point. The pipeline stores up to 5 scenes
    /// per asset from a score of 0.15 (Ml.SceneClassification.MinScore), which is
    /// a fine bar for "let the user search for it" and a terrible one for "build a
    /// keepsake out of it": at 0.15 across 365 softmax classes the model is barely
    /// guessing. <see cref="HasScene"/> stays unfiltered because smart-album rules
    /// have no confidence knob and their users expect recall.
    /// </summary>
    public static Expression<Func<Asset, bool>> HasAnySceneAbove(
        IReadOnlyList<string> labels, float minConfidence)
    {
        var normalized = labels.Select(l => l.Trim().ToLowerInvariant()).ToList();
        return a => a.ClassifiedScenes.Any(s =>
            normalized.Contains(s.Label.ToLower()) && s.Confidence >= minConfidence);
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
