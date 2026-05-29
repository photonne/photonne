using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Caches the per-user set of folder ids a user can read against, so the
/// timeline-style endpoints don't re-load every Folder + FolderPermission +
/// ExternalLibraryPermission row on every page request. Singleton so the
/// cache lives across requests; the underlying queries still run inside the
/// caller's scoped <see cref="ApplicationDbContext"/>.
///
/// TTL is intentionally short (30 s): permission changes are rare, so we
/// don't bother piping explicit invalidations through every admin path —
/// the staleness window is shorter than any human-driven re-test cycle.
/// Callers that mutate folder/library permissions can still call
/// <see cref="Invalidate"/> to drop the entry early.
/// </summary>
public class AllowedFolderCache
{
    private static readonly TimeSpan Ttl = TimeSpan.FromSeconds(30);
    private readonly IMemoryCache _cache;

    public AllowedFolderCache(IMemoryCache cache)
    {
        _cache = cache;
    }

    public Task<HashSet<Guid>> GetAllowedFolderIdsAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string userRootPath,
        CancellationToken ct)
    {
        var key = $"allowed-folders:{userId}";
        return _cache.GetOrCreateAsync(key, async entry =>
        {
            entry.AbsoluteExpirationRelativeToNow = Ttl;
            return await ComputeAsync(dbContext, userId, userRootPath, ct);
        })!;
    }

    public void Invalidate(Guid userId) => _cache.Remove($"allowed-folders:{userId}");

    private static async Task<HashSet<Guid>> ComputeAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string userRootPath,
        CancellationToken ct)
    {
        // Mirror of the logic that used to live inline in TimelineEndpoint
        // (folders with an explicit CanRead grant + folders under the user's
        // personal root with no explicit grants + folders inside an external
        // library the user can read).
        var allFolders = await dbContext.Folders
            .AsNoTracking()
            .Select(f => new { f.Id, f.Path, f.ExternalLibraryId })
            .ToListAsync(ct);

        // Structural containers (/assets, /assets/users) must never grant
        // access even if a stale FolderPermission row points at them — they
        // contain every user's personal space.
        var structuralIds = allFolders
            .Where(f => VirtualPath.IsStructuralContainer(f.Path))
            .Select(f => f.Id)
            .ToHashSet();

        var grantedFolderIds = await dbContext.FolderPermissions
            .AsNoTracking()
            .Where(p => p.UserId == userId && p.CanRead)
            .Select(p => p.FolderId)
            .ToListAsync(ct);

        var foldersWithAnyPermission = await dbContext.FolderPermissions
            .AsNoTracking()
            .Select(p => p.FolderId)
            .Distinct()
            .ToHashSetAsync(ct);

        var allowedIds = grantedFolderIds.Where(id => !structuralIds.Contains(id)).ToHashSet();
        foreach (var f in allFolders)
        {
            if (!foldersWithAnyPermission.Contains(f.Id) && VirtualPath.IsUnder(f.Path, userRootPath))
            {
                allowedIds.Add(f.Id);
            }
        }

        var accessibleLibraryIds = await dbContext.ExternalLibraryPermissions
            .AsNoTracking()
            .Where(p => p.UserId == userId && p.CanRead)
            .Select(p => p.ExternalLibraryId)
            .ToHashSetAsync(ct);

        foreach (var f in allFolders)
        {
            if (f.ExternalLibraryId.HasValue && accessibleLibraryIds.Contains(f.ExternalLibraryId.Value))
            {
                allowedIds.Add(f.Id);
            }
        }

        return allowedIds;
    }
}
