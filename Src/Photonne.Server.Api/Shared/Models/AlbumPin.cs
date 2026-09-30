namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// A user's personal pin on an album (owned or shared with them). Kept apart
/// from <see cref="AlbumPermission"/> because owners have no permission row,
/// and pinning must never touch what other members see.
/// </summary>
public class AlbumPin
{
    // Composite PK: (UserId, AlbumId)
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    public Guid AlbumId { get; set; }
    public Album Album { get; set; } = null!;

    public DateTime PinnedAt { get; set; } = DateTime.UtcNow;
}
