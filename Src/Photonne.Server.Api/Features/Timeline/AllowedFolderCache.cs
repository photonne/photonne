using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Caches the per-user set of folder ids a user can read, so the
/// timeline-style endpoints don't re-load every Folder + FolderPermission +
/// ExternalLibraryPermission row on every page request. This is THE single
/// source of truth for read-side folder visibility (explicit grants inherited
/// across the subtree + unconditional personal space + external libraries) —
/// every asset-listing endpoint must use it instead of inlining its own copy.
/// Singleton so the cache lives across requests; the underlying queries still
/// run inside the caller's scoped <see cref="ApplicationDbContext"/>.
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

    /// <summary>
    /// Per-user setting key holding a JSON array of folder ids the user has
    /// opted OUT of their discovery surfaces (timeline, memories, people,
    /// search, map…). Stored under the user's own Setting.OwnerId. The folder
    /// stays fully browsable/administrable via FoldersEndpoint (which does NOT
    /// derive from this cache) — only the read-side "my content" set shrinks.
    /// </summary>
    public const string ExcludedFoldersSettingKey = "TimelinePrefs.ExcludedFolders";

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
        // Effective read access, mirroring the user-facing sharing model:
        //   1. Explicit CanRead grants. A grant on a folder covers its WHOLE
        //      SUBTREE — inheritance is resolved here at read time, no
        //      per-subfolder rows are materialized. Grants live on the share
        //      roots (/assets/shared/{name}); structural containers never
        //      grant anything even if a stale row points at them.
        //   2. Personal space. Everything under /assets/users/{username} is
        //      always visible to its owner, REGARDLESS of any permission rows
        //      on those folders — a spurious row must not hide personal
        //      content (FoldersEndpoint applies the same priority).
        //   3. External libraries. Per-library permission covers every folder
        //      tagged with that ExternalLibraryId.
        var allFolders = await dbContext.Folders
            .AsNoTracking()
            .Select(f => new { f.Id, f.Path, f.ParentFolderId, f.ExternalLibraryId })
            .ToListAsync(ct);

        var structuralIds = allFolders
            .Where(f => VirtualPath.IsStructuralContainer(f.Path))
            .Select(f => f.Id)
            .ToHashSet();

        var grantedFolderIds = await dbContext.FolderPermissions
            .AsNoTracking()
            .Where(p => p.UserId == userId && p.CanRead)
            .Select(p => p.FolderId)
            .ToListAsync(ct);

        var allowedIds = grantedFolderIds.Where(id => !structuralIds.Contains(id)).ToHashSet();

        // Inheritance: breadth-first walk down from every granted folder.
        // External-library folders are skipped — their visibility is governed
        // per-library below, not by folder grants.
        var childrenByParent = allFolders
            .Where(f => f.ParentFolderId.HasValue)
            .ToLookup(f => f.ParentFolderId!.Value);
        var pending = new Stack<Guid>(allowedIds);
        while (pending.Count > 0)
        {
            var current = pending.Pop();
            foreach (var child in childrenByParent[current])
            {
                if (child.ExternalLibraryId.HasValue) continue;
                if (allowedIds.Add(child.Id)) pending.Push(child.Id);
            }
        }

        // Personal space — unconditional (no permission-row gate).
        foreach (var f in allFolders)
        {
            if (!f.ExternalLibraryId.HasValue
                && !structuralIds.Contains(f.Id)
                && VirtualPath.IsUnder(f.Path, userRootPath))
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

        // Per-user opt-out: a folder the user only ADMINISTERS should not bleed
        // into their timeline/memories/people/search. Subtract each excluded
        // root together with its whole subtree (grants are inherited, so the
        // children were pulled into allowedIds above). Applied last, after
        // personal space and libraries — excluded roots are always shared
        // folders, so this never touches the user's own personal content.
        var excludedRootIds = await GetExcludedFolderIdsAsync(dbContext, userId, ct);
        if (excludedRootIds.Count > 0)
        {
            var excludedSubtree = new HashSet<Guid>();
            var pendingExcluded = new Stack<Guid>(excludedRootIds);
            while (pendingExcluded.Count > 0)
            {
                var current = pendingExcluded.Pop();
                if (!excludedSubtree.Add(current)) continue;
                foreach (var child in childrenByParent[current]) pendingExcluded.Push(child.Id);
            }
            allowedIds.ExceptWith(excludedSubtree);
        }

        return allowedIds;
    }

    /// <summary>
    /// Reads the user's opt-out folder set from their <c>TimelinePrefs.ExcludedFolders</c>
    /// setting. Returns an empty set when unset or unparseable. Shared by the
    /// visibility computation, the Folders DTO (to render the toggle state) and
    /// the toggle endpoint (read-modify-write).
    /// </summary>
    public static async Task<HashSet<Guid>> GetExcludedFolderIdsAsync(
        ApplicationDbContext dbContext, Guid userId, CancellationToken ct)
    {
        var raw = await dbContext.Settings
            .AsNoTracking()
            .Where(s => s.OwnerId == userId && s.Key == ExcludedFoldersSettingKey)
            .Select(s => s.Value)
            .FirstOrDefaultAsync(ct);
        return ParseFolderIds(raw);
    }

    public static HashSet<Guid> ParseFolderIds(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return new HashSet<Guid>();
        try
        {
            var ids = JsonSerializer.Deserialize<List<Guid>>(raw);
            return ids is null ? new HashSet<Guid>() : ids.ToHashSet();
        }
        catch (JsonException)
        {
            return new HashSet<Guid>();
        }
    }
}
