using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;

namespace Photonne.Server.Api.Features.Pins;

/// <summary>
/// "Fijados" is one list per user across two tables (AlbumPins, FolderPins),
/// so its order is one sequence of positions spread over both.
/// </summary>
public static class PinOrder
{
    /// <summary>The position that puts a new pin first: one above the user's current top.</summary>
    public static async Task<int> TopPositionAsync(
        ApplicationDbContext dbContext, Guid userId, CancellationToken cancellationToken)
    {
        var albumTop = await dbContext.AlbumPins
            .Where(p => p.UserId == userId)
            .MinAsync(p => (int?)p.Position, cancellationToken);
        var folderTop = await dbContext.FolderPins
            .Where(p => p.UserId == userId)
            .MinAsync(p => (int?)p.Position, cancellationToken);

        var top = albumTop is null ? folderTop
            : folderTop is null ? albumTop
            : Math.Min(albumTop.Value, folderTop.Value);
        return top is { } value ? value - 1 : 0;
    }
}
