using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Organize;

/// <summary>
/// Single visibility predicate for the "Para organizar" inbox: assets that are
/// still sitting under the user's MobileBackup subtree, i.e. dropped there by
/// the automatic backup and not yet filed into any folder. "Organized" means
/// "moved out of MobileBackup", so the inbox drains to zero as the user files
/// things away.
///
/// Deliberately NOT routed through <c>TimelineQuery.VisibleAssets</c>: that
/// predicate scopes by the allowed-folder set (and thus the per-user timeline
/// opt-out), which is irrelevant here — the inbox is always the caller's own
/// personal MobileBackup. Both the list and the count endpoints call this so
/// the badge can never disagree with the screen.
/// </summary>
internal static class OrganizeQuery
{
    /// <summary>
    /// Builds the MobileBackup root prefix for a user, with a trailing slash so
    /// a hypothetical sibling like <c>MobileBackup2</c> is never matched.
    /// </summary>
    public static string MobileBackupPrefix(string username) =>
        $"/assets/users/{username}/MobileBackup/";

    /// <summary>
    /// Non-deleted, non-archived, present-on-disk assets under the user's
    /// MobileBackup subtree, excluding the motion (.mov) half of a Live Photo
    /// (mirrors <c>TimelineQuery.VisibleAssets</c>).
    /// </summary>
    public static IQueryable<Asset> Pending(
        ApplicationDbContext dbContext,
        string username)
    {
        var prefix = MobileBackupPrefix(username);
        return dbContext.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                     && a.FullPath.StartsWith(prefix)
                     && !a.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart));
    }
}
