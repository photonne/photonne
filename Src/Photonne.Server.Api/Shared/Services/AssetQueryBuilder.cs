using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Structured, serializable set of asset-matching criteria. Mirrors the query
/// parameters of <c>/api/assets/search</c> so a single filter-composition
/// implementation (<see cref="AssetQueryBuilder"/>) can back both search and
/// the upcoming smart-albums feature (docs/smart-albums/). All criteria combine
/// with AND, exactly as the search endpoint does today; the boolean rule TREE
/// (AND/OR/NOT) that smart albums need is layered on top of this in a later
/// phase — this type is the flat leaf-criteria carrier.
/// </summary>
public sealed class AssetFilter
{
    /// <summary>Free text over filename, path, caption, user-tag names, OCR text and tag-type name.</summary>
    public string? Text { get; init; }

    /// <summary>Inclusive lower bound on <see cref="Asset.CapturedAt"/>.</summary>
    public DateTime? From { get; init; }

    /// <summary>Upper bound on <see cref="Asset.CapturedAt"/>; the full selected day is included.</summary>
    public DateTime? To { get; init; }

    /// <summary>Substring matched against <see cref="Asset.FullPath"/>.</summary>
    public string? FolderPath { get; init; }

    /// <summary>
    /// Person ids the asset must ALL appear in (intersection). Identity is
    /// per-user, so these are resolved against <paramref name="identityUserId"/>
    /// passed to <see cref="AssetQueryBuilder.Apply"/>. The caller is
    /// responsible for validating ownership of these ids first.
    /// </summary>
    public IReadOnlyList<Guid>? PersonIds { get; init; }

    /// <summary>Object-detection labels the asset must ALL have (intersection).</summary>
    public IReadOnlyList<string>? ObjectLabels { get; init; }

    /// <summary>Scene labels the asset must ALL have (intersection).</summary>
    public IReadOnlyList<string>? SceneLabels { get; init; }

    /// <summary>Websearch tsquery matched against OCR-recognized text lines.</summary>
    public string? OcrText { get; init; }
}

/// <summary>
/// Composes the asset-matching <c>.Where(...)</c> chain shared by search and
/// smart albums. It applies ONLY the match criteria in <see cref="AssetFilter"/>;
/// visibility gating (deleted/archived/folder scope, owner/album scope) stays
/// with the caller — search gates by allowed folders, smart albums will gate by
/// the owner+viewer visibility intersection (see docs/smart-albums/README.md §7).
///
/// Behaviour is a straight extraction of Features/Search/SearchEndpoint.cs and
/// must stay byte-for-byte equivalent to it.
/// </summary>
public static class AssetQueryBuilder
{
    /// <summary>
    /// Applies every populated criterion in <paramref name="filter"/> to
    /// <paramref name="query"/> and returns the narrowed query.
    /// </summary>
    /// <param name="query">Base query, already visibility-gated by the caller.</param>
    /// <param name="filter">Criteria to apply (empty criteria are skipped).</param>
    /// <param name="db">Context used for the per-user face-identity subquery.</param>
    /// <param name="identityUserId">
    /// User whose <see cref="UserFaceAssignment"/> rows resolve the person
    /// filter. For search this is the requester; for smart albums it is the
    /// album owner (owner-anchored resolution).
    /// </param>
    public static IQueryable<Asset> Apply(
        IQueryable<Asset> query,
        AssetFilter filter,
        ApplicationDbContext db,
        Guid identityUserId)
    {
        // Every leaf predicate comes from AssetConditions — the same source
        // smart-album rules compile against — so search and smart albums match
        // identically. Criteria chain with .Where (AND), preserving search's
        // long-standing intersection semantics for objects/scenes/people.
        if (!string.IsNullOrWhiteSpace(filter.Text))
            query = query.Where(AssetConditions.Text(filter.Text));

        if (filter.From.HasValue || filter.To.HasValue)
            query = query.Where(AssetConditions.CapturedBetween(filter.From, filter.To));

        if (!string.IsNullOrWhiteSpace(filter.FolderPath))
            query = query.Where(AssetConditions.FullPathContains(filter.FolderPath));

        foreach (var label in Normalize(filter.ObjectLabels))
            query = query.Where(AssetConditions.HasObject(label));

        foreach (var label in Normalize(filter.SceneLabels))
            query = query.Where(AssetConditions.HasScene(label));

        if (!string.IsNullOrWhiteSpace(filter.OcrText))
            query = query.Where(AssetConditions.Ocr(filter.OcrText));

        if (filter.PersonIds is { Count: > 0 })
            foreach (var pid in filter.PersonIds)
                query = query.Where(AssetConditions.HasPerson(db, identityUserId, pid));

        return query;
    }

    /// <summary>Pliega las etiquetas pedidas igual que se pliegan las guardadas al
    /// compararlas (minúsculas y sin acentos), ver <see cref="SearchText"/>.</summary>
    private static IEnumerable<string> Normalize(IReadOnlyList<string>? labels) =>
        labels is null
            ? Enumerable.Empty<string>()
            : labels.Where(l => !string.IsNullOrWhiteSpace(l))
                    .Select(SearchText.Fold)
                    .Distinct();
}
