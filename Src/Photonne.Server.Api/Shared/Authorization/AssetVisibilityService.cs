using System.Linq.Expressions;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Shared.Authorization;

/// <summary>
/// Computes the set of <see cref="Asset"/>s a user is allowed to read and
/// exposes it both as ID buckets (cheap to <c>Contains</c>-translate) and as a
/// composable predicate. Used by per-user face clustering and the People
/// endpoints so a user with read access to a shared album/folder/external
/// library sees faces detected on those assets — but not on others.
///
/// The four buckets that grant visibility are unioned:
///   * <see cref="Asset.OwnerId"/> equals the requesting user;
///   * the asset lives in a <see cref="Folder"/> the user can read per
///     <see cref="Features.Timeline.AllowedFolderCache"/> (explicit grants
///     inherited across the subtree + unconditional personal root +
///     external-library folders);
///   * the asset lives in an <see cref="ExternalLibrary"/> the user has an
///     <see cref="ExternalLibraryPermission"/> for;
///   * the asset is included in any <see cref="Album"/> the user has an
///     <see cref="AlbumPermission"/> with <c>CanRead</c>.
///
/// Scopes are computed once per request, cached on the instance, and re-used
/// across queries within the same scope. Don't long-live them across requests
/// — permissions can change.
///
/// Note the difference between <see cref="AssetVisibilityScope.AssetPredicate"/>
/// ("may I read this?") and <see cref="AssetVisibilityScope.DiscoveryPredicate"/>
/// ("do I want this surfacing at me?"). Permission is a union, so it can only
/// ever grant; a preference has to subtract, and subtracting from a union is not
/// something you can express by adding another bucket to it.
/// </summary>
public class AssetVisibilityService
{
    private readonly ApplicationDbContext _db;
    private readonly UserStorageService _userStorage;
    private readonly AllowedFolderCache _allowedFolders;

    public AssetVisibilityService(
        ApplicationDbContext db,
        UserStorageService userStorage,
        AllowedFolderCache allowedFolders)
    {
        _db = db;
        _userStorage = userStorage;
        _allowedFolders = allowedFolders;
    }

    /// <summary>
    /// Pre-fetches the four ID buckets for the given user. Call once per request.
    /// </summary>
    public async Task<AssetVisibilityScope> GetScopeAsync(Guid userId, CancellationToken ct)
    {
        // Personal-root semantics key on the USERNAME path, so the fallback
        // must resolve it from the Users table — the previous
        // "/assets/users/{userId}" GUID fallback silently disabled the
        // personal-space rule whenever GetVirtualRootAsync returned null.
        var userRootPath = await _userStorage.GetVirtualRootAsync(userId, ct);
        if (string.IsNullOrEmpty(userRootPath))
        {
            var username = await _db.Users
                .AsNoTracking()
                .Where(u => u.Id == userId)
                .Select(u => u.Username)
                .FirstOrDefaultAsync(ct);
            userRootPath = $"/assets/users/{username}";
        }

        // Folder visibility — single source of truth shared with the timeline
        // endpoints (explicit grants inherited across the subtree +
        // unconditional personal space + external-library folders).
        var allowedFolderIds = await _allowedFolders.GetAllowedFolderIdsAsync(
            _db, userId, userRootPath, ct);

        // External libraries the user can read (also exposed independently on
        // the scope for the ExternalLibraryId leg of the predicate).
        var allowedExternalLibraryIds = await _db.ExternalLibraryPermissions
            .AsNoTracking()
            .Where(p => p.UserId == userId && p.CanRead)
            .Select(p => p.ExternalLibraryId)
            .ToHashSetAsync(ct);

        // Assets reachable through a readable Album. Pre-fetch the AssetIds so
        // the runtime predicate is a simple IN clause.
        var albumVisibleAssetIds = await _db.AlbumPermissions
            .AsNoTracking()
            .Where(p => p.UserId == userId && p.CanRead)
            .SelectMany(p => p.Album.AlbumAssets.Select(aa => aa.AssetId))
            .Distinct()
            .ToHashSetAsync(ct);

        // The folders this user opted out of their discovery surfaces, with their
        // subtrees. Kept apart from allowedFolderIds (which already has them
        // subtracted) because the other three buckets grant on their own: an
        // asset the user uploaded to a shared folder they administer matches
        // OwnerId, and the folder rule never gets a say.
        var excludedFolderIds = await _allowedFolders.GetExcludedFolderSubtreeAsync(_db, userId, ct);

        return new AssetVisibilityScope(
            userId,
            allowedFolderIds,
            allowedExternalLibraryIds,
            albumVisibleAssetIds,
            excludedFolderIds);
    }
}

/// <summary>
/// Snapshot of what a single user can read at a single moment in time. Use the
/// expression returned by <see cref="AssetPredicate"/> directly inside EF
/// queries — the underlying <see cref="HashSet{T}.Contains(T)"/> calls
/// translate to SQL <c>IN (...)</c> clauses.
/// </summary>
public sealed class AssetVisibilityScope
{
    public Guid UserId { get; }
    public IReadOnlySet<Guid> AllowedFolderIds { get; }
    public IReadOnlySet<Guid> AllowedExternalLibraryIds { get; }
    public IReadOnlySet<Guid> AlbumVisibleAssetIds { get; }

    /// <summary>Shared folders (and their subtrees) the user opted out of their
    /// discovery surfaces. Readable, just not something they want resurfaced.</summary>
    public IReadOnlySet<Guid> ExcludedFolderIds { get; }

    public AssetVisibilityScope(
        Guid userId,
        IReadOnlySet<Guid> allowedFolderIds,
        IReadOnlySet<Guid> allowedExternalLibraryIds,
        IReadOnlySet<Guid> albumVisibleAssetIds,
        IReadOnlySet<Guid> excludedFolderIds)
    {
        UserId = userId;
        AllowedFolderIds = allowedFolderIds;
        AllowedExternalLibraryIds = allowedExternalLibraryIds;
        AlbumVisibleAssetIds = albumVisibleAssetIds;
        ExcludedFolderIds = excludedFolderIds;
    }

    /// <summary>Predicate over <see cref="Asset"/>: true when the user can read
    /// this asset. Translates to four UNION-able SQL fragments (owner equality
    /// + three IN clauses).
    ///
    /// This is authorization, and authorization only. For anything that goes out
    /// and finds photos on the user's behalf — memories, search, smart albums —
    /// use <see cref="DiscoveryPredicate"/> instead.</summary>
    public Expression<Func<Asset, bool>> AssetPredicate()
    {
        // Capture as locals so the closure carries the sets, not the scope ref.
        var userId = UserId;
        var folders = AllowedFolderIds;
        var libs = AllowedExternalLibraryIds;
        var albums = AlbumVisibleAssetIds;
        return a =>
            a.OwnerId == userId
            || (a.FolderId.HasValue && folders.Contains(a.FolderId.Value))
            || (a.ExternalLibraryId.HasValue && libs.Contains(a.ExternalLibraryId.Value))
            || albums.Contains(a.Id);
    }

    /// <summary>
    /// <see cref="AssetPredicate"/> minus the folders the user opted out of.
    /// What every surface that resurfaces photos at the user must query through.
    ///
    /// The subtraction can't be folded into the union above. That predicate ORs
    /// four ways in, and the folder rule is only one of them: a photo the user
    /// uploaded to a shared folder they merely administer still matches
    /// <c>OwnerId</c>, and one that also sits in a shared album still matches
    /// <c>albums</c>. Excluding the folder from <c>AllowedFolderIds</c> — which
    /// AllowedFolderCache does — therefore hides it from the timeline (which
    /// gates on folders alone) while memories and search, which gate on the
    /// union, went on serving it. That was the bug.
    /// </summary>
    public Expression<Func<Asset, bool>> DiscoveryPredicate()
    {
        var excluded = ExcludedFolderIds;
        if (excluded.Count == 0) return AssetPredicate();

        var userId = UserId;
        var folders = AllowedFolderIds;
        var libs = AllowedExternalLibraryIds;
        var albums = AlbumVisibleAssetIds;
        // Spelled out rather than composed from AssetPredicate(): EF translates
        // one expression tree, and invoking another lambda inside it doesn't
        // translate.
        return a =>
            (a.OwnerId == userId
                || (a.FolderId.HasValue && folders.Contains(a.FolderId.Value))
                || (a.ExternalLibraryId.HasValue && libs.Contains(a.ExternalLibraryId.Value))
                || albums.Contains(a.Id))
            && (!a.FolderId.HasValue || !excluded.Contains(a.FolderId.Value));
    }

    /// <summary>True when this scope does not need to filter (typical for an
    /// admin-style global pass). Always false here, but keeps callers honest
    /// about evaluating visibility before short-circuiting.</summary>
    public bool IsUnrestricted => false;
}
