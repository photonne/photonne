using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.SmartAlbums;

/// <summary>
/// Turns a smart-album rule into a live <c>IQueryable&lt;Asset&gt;</c>: expands
/// folder subtrees, compiles the rule owner-anchored, and gates the result by
/// visibility. The base gate mirrors <see cref="Features.Timeline.TimelineQuery"/>
/// (not deleted/archived/missing, not a Live-Photo motion part) and the scope
/// gate uses <see cref="AssetVisibilityService"/>: the OWNER's scope always, and
/// — when a viewer other than the owner opens a shared album — intersected with
/// the VIEWER's scope so nobody sees an asset they can't reach
/// (docs/smart-albums/README.md §7).
/// </summary>
public sealed class SmartAlbumResolver
{
    private readonly ApplicationDbContext _db;
    private readonly AssetVisibilityService _visibility;

    public SmartAlbumResolver(ApplicationDbContext db, AssetVisibilityService visibility)
    {
        _db = db;
        _visibility = visibility;
    }

    /// <param name="ownerId">Album owner — resolves person identities and the primary scope.</param>
    /// <param name="viewerId">User opening the album; equals owner during creation/preview.</param>
    public async Task<IQueryable<Asset>> ResolveAsync(
        SmartRuleNode root, Guid ownerId, Guid viewerId, CancellationToken ct)
    {
        await ExpandFoldersAsync(root, ct);
        var rule = SmartRuleCompiler.Compile(root, _db, ownerId);

        var ownerScope = await _visibility.GetScopeAsync(ownerId, ct);
        var query = _db.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                     && !a.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart))
            .Where(ownerScope.AssetPredicate());

        if (viewerId != ownerId)
        {
            var viewerScope = await _visibility.GetScopeAsync(viewerId, ct);
            query = query.Where(viewerScope.AssetPredicate());
        }

        return query.Where(rule);
    }

    /// <summary>
    /// Rewrites every folder condition's <c>FolderIds</c> to include the whole
    /// subtree (unless <c>IncludeSubfolders == false</c>). Descendants are found
    /// by path prefix — folders carry a unique virtual <see cref="Folder.Path"/>,
    /// so a child's path starts with the parent's path + "/".
    /// </summary>
    private async Task ExpandFoldersAsync(SmartRuleNode? node, CancellationToken ct)
    {
        if (node is null) return;

        if (node.Conditions is { Count: > 0 })
            foreach (var child in node.Conditions)
                await ExpandFoldersAsync(child, ct);
        if (node.Condition is not null)
            await ExpandFoldersAsync(node.Condition, ct);

        var isFolder = string.Equals(node.Type, "folder", StringComparison.OrdinalIgnoreCase);
        if (!isFolder || node.FolderIds is not { Count: > 0 } || node.IncludeSubfolders == false)
            return;

        var ids = node.FolderIds;
        var paths = await _db.Folders.AsNoTracking()
            .Where(f => ids.Contains(f.Id))
            .Select(f => f.Path)
            .ToListAsync(ct);
        if (paths.Count == 0) return;

        var prefixPredicate = PredicateBuilder.False<Folder>();
        foreach (var path in paths)
        {
            var p = path;
            prefixPredicate = prefixPredicate.Or(f => f.Path == p || f.Path.StartsWith(p + "/"));
        }

        node.FolderIds = await _db.Folders.AsNoTracking()
            .Where(prefixPredicate)
            .Select(f => f.Id)
            .ToListAsync(ct);
    }
}
