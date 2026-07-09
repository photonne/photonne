using System.Linq.Expressions;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.SmartAlbums;

/// <summary>Thrown when a rule tree is structurally invalid or references an
/// unknown condition type; the endpoint maps it to HTTP 400.</summary>
public sealed class SmartRuleException : Exception
{
    public SmartRuleException(string message) : base(message) { }
}

/// <summary>
/// Compiles a <see cref="SmartRuleNode"/> tree into a single EF-translatable
/// <c>Expression&lt;Func&lt;Asset,bool&gt;&gt;</c>, combining AND/OR/NOT via
/// <see cref="PredicateBuilder"/> and delegating every leaf to
/// <see cref="AssetConditions"/> (the same predicates search uses). People are
/// resolved against <paramref name="identityUserId"/> — the album OWNER, for
/// owner-anchored resolution (docs/smart-albums/README.md §7).
///
/// Folder conditions must be subtree-expanded BEFORE compiling (see
/// <see cref="SmartAlbumResolver"/>); the compiler treats <c>FolderIds</c> as a
/// literal id set.
/// </summary>
public static class SmartRuleCompiler
{
    private const string MatchAll = "all";
    private const string MatchAny = "any";

    public static Expression<Func<Asset, bool>> Compile(
        SmartRuleNode? node, ApplicationDbContext db, Guid identityUserId)
    {
        if (node is null)
            return PredicateBuilder.True<Asset>();

        // Logical node
        if (!string.IsNullOrWhiteSpace(node.Op))
            return CompileLogical(node, db, identityUserId);

        // Condition leaf
        var type = node.Type?.Trim().ToLowerInvariant();
        return type switch
        {
            "not" => Compile(node.Condition, db, identityUserId).Not(),
            "person" => CompilePerson(node, db, identityUserId),
            "folder" => node.FolderIds is { Count: > 0 }
                ? AssetConditions.FolderIn(node.FolderIds.ToHashSet())
                : PredicateBuilder.False<Asset>(),
            "daterange" => AssetConditions.CapturedBetween(node.From, node.To),
            "scene" => CompileLabels(node, AssetConditions.HasScene, defaultMatch: MatchAll),
            "object" => CompileLabels(node, AssetConditions.HasObject, defaultMatch: MatchAll),
            "tag" => CompileTag(node),
            "favorite" => AssetConditions.IsFavorite(node.Value ?? true),
            "mediatype" => CompileMediaType(node),
            "ocr" => string.IsNullOrWhiteSpace(node.Query)
                ? PredicateBuilder.True<Asset>()
                : AssetConditions.Ocr(node.Query),
            "text" => string.IsNullOrWhiteSpace(node.Query)
                ? PredicateBuilder.True<Asset>()
                : AssetConditions.Text(node.Query),
            null or "" => throw new SmartRuleException("Rule node has neither 'op' nor 'type'."),
            _ => throw new SmartRuleException($"Unknown condition type '{node.Type}'.")
        };
    }

    private static Expression<Func<Asset, bool>> CompileLogical(
        SmartRuleNode node, ApplicationDbContext db, Guid identityUserId)
    {
        var isOr = string.Equals(node.Op, "OR", StringComparison.OrdinalIgnoreCase);
        if (!isOr && !string.Equals(node.Op, "AND", StringComparison.OrdinalIgnoreCase))
            throw new SmartRuleException($"Unknown logical operator '{node.Op}'.");

        var children = (node.Conditions ?? new List<SmartRuleNode>())
            .Select(c => Compile(c, db, identityUserId))
            .ToList();

        // Empty AND matches everything (no constraint); empty OR matches nothing.
        if (children.Count == 0)
            return isOr ? PredicateBuilder.False<Asset>() : PredicateBuilder.True<Asset>();

        var combined = children[0];
        for (var i = 1; i < children.Count; i++)
            combined = isOr ? combined.Or(children[i]) : combined.And(children[i]);
        return combined;
    }

    private static Expression<Func<Asset, bool>> CompilePerson(
        SmartRuleNode node, ApplicationDbContext db, Guid identityUserId)
    {
        if (node.PersonIds is not { Count: > 0 })
            return PredicateBuilder.False<Asset>();

        var ids = node.PersonIds.Distinct().ToList();

        // Default "any" — an album "de mis hijos" wants photos with EITHER kid,
        // not only the ones where they appear together (docs/smart-albums/creation-ux.md).
        if (!IsMatchAll(node.Match))
            return AssetConditions.HasAnyPerson(db, identityUserId, ids);

        var combined = AssetConditions.HasPerson(db, identityUserId, ids[0]);
        for (var i = 1; i < ids.Count; i++)
            combined = combined.And(AssetConditions.HasPerson(db, identityUserId, ids[i]));
        return combined;
    }

    private static Expression<Func<Asset, bool>> CompileLabels(
        SmartRuleNode node, Func<string, Expression<Func<Asset, bool>>> factory, string defaultMatch)
    {
        var labels = (node.Labels ?? new List<string>())
            .Where(l => !string.IsNullOrWhiteSpace(l))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();

        if (labels.Count == 0)
            return PredicateBuilder.True<Asset>();

        var match = string.IsNullOrWhiteSpace(node.Match) ? defaultMatch : node.Match;
        var isAny = string.Equals(match, MatchAny, StringComparison.OrdinalIgnoreCase);

        var combined = factory(labels[0]);
        for (var i = 1; i < labels.Count; i++)
            combined = isAny ? combined.Or(factory(labels[i])) : combined.And(factory(labels[i]));
        return combined;
    }

    private static Expression<Func<Asset, bool>> CompileTag(SmartRuleNode node)
    {
        if (!string.IsNullOrWhiteSpace(node.TagType))
        {
            if (!Enum.TryParse<AssetTagType>(node.TagType, ignoreCase: true, out var parsed))
                throw new SmartRuleException($"Unknown tag type '{node.TagType}'.");
            return AssetConditions.HasTagType(parsed);
        }

        if (node.UserTagIds is { Count: > 0 })
        {
            // Match=any by default: "cualquiera de estos tags".
            var isAll = IsMatchAll(node.Match);
            var ids = node.UserTagIds.Distinct().ToList();
            var combined = AssetConditions.HasUserTag(ids[0]);
            for (var i = 1; i < ids.Count; i++)
                combined = isAll ? combined.And(AssetConditions.HasUserTag(ids[i]))
                                 : combined.Or(AssetConditions.HasUserTag(ids[i]));
            return combined;
        }

        throw new SmartRuleException("Tag condition needs 'tagType' or 'userTagIds'.");
    }

    private static Expression<Func<Asset, bool>> CompileMediaType(SmartRuleNode node)
    {
        if (string.IsNullOrWhiteSpace(node.MediaType)
            || !Enum.TryParse<AssetType>(node.MediaType, ignoreCase: true, out var type))
            throw new SmartRuleException($"Unknown media type '{node.MediaType}'.");
        return AssetConditions.OfType(type);
    }

    private static bool IsMatchAll(string? match) =>
        string.Equals(match, MatchAll, StringComparison.OrdinalIgnoreCase);
}
