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

        return new AssetVisibilityScope(
            userId,
            allowedFolderIds,
            allowedExternalLibraryIds,
            albumVisibleAssetIds);
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

    public AssetVisibilityScope(
        Guid userId,
        IReadOnlySet<Guid> allowedFolderIds,
        IReadOnlySet<Guid> allowedExternalLibraryIds,
        IReadOnlySet<Guid> albumVisibleAssetIds)
    {
        UserId = userId;
        AllowedFolderIds = allowedFolderIds;
        AllowedExternalLibraryIds = allowedExternalLibraryIds;
        AlbumVisibleAssetIds = albumVisibleAssetIds;
    }

    /// <summary>Predicate over <see cref="Asset"/>: true when the user can read
    /// this asset. Translates to four UNION-able SQL fragments (owner equality
    /// + three IN clauses).</summary>
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

    /// <summary>True when this scope does not need to filter (typical for an
    /// admin-style global pass). Always false here, but keeps callers honest
    /// about evaluating visibility before short-circuiting.</summary>
    public bool IsUnrestricted => false;
}
