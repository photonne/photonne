using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// The folders the server keeps in the thumbnails volume next to the
/// per-asset ones (which are named after the asset's GUID, so maintenance
/// never mistakes these for orphans). They used to start with a dot, which
/// hid them in NAS file managers: the video copies filled a disk with nothing
/// visible to show for it.
/// </summary>
public static class ThumbnailFolders
{
    public const string Playback = "playback";
    public const string VideoSummaries = "video-summaries";

    private static readonly (string Old, string New)[] Renamed =
    [
        (".playback", Playback),
        (".video-summaries", VideoSummaries),
    ];

    /// <summary>
    /// Moves the old dot folders to their visible names, and points the
    /// playback rows that were in the old one at the new one. A rename on the
    /// same disk, so instant whatever their size; when both exist (an old
    /// server wrote to the dot folder after a newer one ran) the files are
    /// merged in.
    /// </summary>
    public static async Task MoveLegacyAsync(string thumbnailsPath, ApplicationDbContext db, ILogger logger, CancellationToken ct = default)
    {
        foreach (var (oldName, newName) in Renamed)
        {
            var from = Path.Combine(thumbnailsPath, oldName);
            var to = Path.Combine(thumbnailsPath, newName);
            if (!Directory.Exists(from)) continue;
            try
            {
                if (Directory.Exists(to)) Merge(from, to);
                else Directory.Move(from, to);
                logger.LogInformation("Moved {From} to {To}", from, to);
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
            {
                logger.LogWarning(ex, "Could not move {From} to {To}; it is left where it is", from, to);
                continue;
            }

            if (newName == Playback)
            {
                // Not worth failing the boot over: the playback sweep also finds
                // each copy in the new folder by its id.
                try
                {
                    var prefix = from + Path.DirectorySeparatorChar;
                    var moved = await db.AssetPlaybacks
                        .Where(p => p.FilePath != null && p.FilePath.StartsWith(prefix))
                        .ExecuteUpdateAsync(s => s.SetProperty(p => p.FilePath, p => to + Path.DirectorySeparatorChar + p.FilePath!.Substring(prefix.Length)), ct);
                    if (moved > 0) logger.LogInformation("Pointed {Count} playback copies at {To}", moved, to);
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    logger.LogWarning(ex, "Could not point the playback copies at {To}; the sweep will", to);
                }
            }
        }
    }

    private static void Merge(string from, string to)
    {
        Directory.CreateDirectory(to);
        foreach (var file in Directory.GetFiles(from))
        {
            var target = Path.Combine(to, Path.GetFileName(file));
            if (File.Exists(target)) File.Delete(file);
            else File.Move(file, target);
        }
        foreach (var directory in Directory.GetDirectories(from))
            Merge(directory, Path.Combine(to, Path.GetFileName(directory)));
        Directory.Delete(from);
    }
}
