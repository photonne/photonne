using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Shared.Authorization;

/// <summary>
/// Write-time companion of the read-time inheritance model: permissions are
/// resolved down the folder tree (a grant on /assets/shared/{name} covers its
/// whole subtree, personal space is implicit), so materializing a
/// FolderPermission row on every folder an upload/sync/index touches only
/// bloats the table. Every auto-grant call site asks this guard first; the
/// explicit-share endpoint never does — a user-initiated grant is always
/// stored as given.
/// </summary>
public static class FolderPermissionGuard
{
    /// <summary>
    /// True when auto-creating a full-access FolderPermission row for
    /// <paramref name="userId"/> on <paramref name="folderId"/> would be
    /// redundant: (a) the folder lives in that user's own personal space
    /// (implicitly fully accessible), or (b) a non-structural ancestor already
    /// carries a full-access row for the user (inherited by the subtree).
    /// </summary>
    public static async Task<bool> IsRedundantFullGrantAsync(
        ApplicationDbContext db,
        Guid userId,
        Guid folderId,
        CancellationToken ct)
    {
        var folder = await db.Folders
            .AsNoTracking()
            .Where(f => f.Id == folderId)
            .Select(f => new { f.Path, f.ParentFolderId })
            .FirstOrDefaultAsync(ct);
        if (folder == null) return false;

        // (a) The user's own personal space.
        var username = await db.Users
            .AsNoTracking()
            .Where(u => u.Id == userId)
            .Select(u => u.Username)
            .FirstOrDefaultAsync(ct);
        if (!string.IsNullOrEmpty(username) &&
            VirtualPath.IsUnder(folder.Path, $"/assets/users/{username}"))
        {
            return true;
        }

        // (b) A full-access grant on any non-structural ancestor. Mirrors the
        // chain walk in FoldersEndpoint.HasInheritedFolderPermissionAsync;
        // folder trees are shallow so per-level queries are fine.
        var parentId = folder.ParentFolderId;
        while (parentId.HasValue)
        {
            var parent = await db.Folders
                .AsNoTracking()
                .Where(f => f.Id == parentId.Value)
                .Select(f => new { f.Id, f.Path, f.ParentFolderId })
                .FirstOrDefaultAsync(ct);
            if (parent == null) break;

            if (!VirtualPath.IsStructuralContainer(parent.Path))
            {
                var covered = await db.FolderPermissions
                    .AsNoTracking()
                    .AnyAsync(p => p.UserId == userId && p.FolderId == parent.Id
                                && p.CanRead && p.CanWrite && p.CanDelete && p.CanManagePermissions, ct);
                if (covered) return true;
            }

            parentId = parent.ParentFolderId;
        }

        return false;
    }
}
