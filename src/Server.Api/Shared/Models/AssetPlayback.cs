using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public enum PlaybackStatus
{
    /// <summary>The original plays everywhere (H.264 in MP4/MOV, up to 1080p): it is served as is.</summary>
    NotNeeded = 0,

    /// <summary>A playback copy exists at <see cref="AssetPlayback.FilePath"/>.</summary>
    Ready = 1,

    /// <summary>ffmpeg couldn't read or convert it; retried a few times, then the original is served.</summary>
    Failed = 2,
}

/// <summary>
/// What a video is played as: the original, or a copy that every browser and
/// phone can play (H.264/AAC MP4 at up to 1080p, ready to seek). One row per
/// video once it has been looked at; deleted with the asset.
/// </summary>
public class AssetPlayback
{
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    public PlaybackStatus Status { get; set; }

    [MaxLength(1000)]
    public string? FilePath { get; set; }

    public int Attempts { get; set; }

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
