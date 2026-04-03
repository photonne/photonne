namespace Photonne.Server.Api.Shared.Models;

public class SharedLink
{
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>URL-safe token used in share links. e.g. /share/{Token}</summary>
    public string Token { get; set; } = string.Empty;

    public Guid? AssetId { get; set; }
    public Asset? Asset { get; set; }

    public Guid? AlbumId { get; set; }
    public Album? Album { get; set; }

    public Guid CreatedById { get; set; }
    public User? CreatedBy { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? ExpiresAt { get; set; }

    /// <summary>PBKDF2 hash of an optional access password.</summary>
    public string? PasswordHash { get; set; }

    /// <summary>Whether recipients may download the content.</summary>
    public bool AllowDownload { get; set; } = true;

    /// <summary>Maximum number of views before the link is automatically revoked. Null = unlimited.</summary>
    public int? MaxViews { get; set; }

    /// <summary>Number of times this link has been successfully accessed.</summary>
    public int ViewCount { get; set; } = 0;

}
