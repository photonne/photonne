namespace Photonne.Client.Web.Models;

public class SharedContentResponse
{
    public string Token { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty; // "asset" | "album"
    public bool RequiresPassword { get; set; }
    public bool WrongPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public SharedAssetInfo? Asset { get; set; }
    public SharedAlbumInfo? Album { get; set; }
    public List<SharedAssetInfo>? Assets { get; set; }
    public DateTime? ExpiresAt { get; set; }
}

public class SharedAssetInfo
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty;
    public DateTime FileCreatedAt { get; set; }
    public long FileSize { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public string ThumbnailUrl { get; set; } = string.Empty;
    public string ContentUrl { get; set; } = string.Empty;

    public double AspectRatio => Width.HasValue && Height.HasValue && Height.Value > 0
        ? (double)Width.Value / Height.Value : 1.0;
}

public class SharedAlbumInfo
{
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public int AssetCount { get; set; }
    public string? CoverThumbnailUrl { get; set; }
}

// ── Public link creation ──────────────────────────────────────────────────────

public class CreateShareLinkRequest
{
    public Guid? AssetId { get; set; }
    public Guid? AlbumId { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public string? Password { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
}

public class CreateShareLinkResponse
{
    public string Token { get; set; } = string.Empty;
    public Guid? AssetId { get; set; }
    public Guid? AlbumId { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public bool HasPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
    public int ViewCount { get; set; }
    /// <summary>
    /// Absolute URL to open the shared content. Built from <c>ServerSettings.PublicUrl</c>
    /// when configured, otherwise from the current request's base URL.
    /// </summary>
    public string ShareUrl { get; set; } = string.Empty;
}

// ── Update share link ─────────────────────────────────────────────────────────

public class UpdateShareLinkRequest
{
    public DateTime? ExpiresAt { get; set; }
    /// <summary>null = keep current | "" = remove | value = set new</summary>
    public string? Password { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
}

public class UpdateShareLinkResponse
{
    public string Token { get; set; } = string.Empty;
    public DateTime? ExpiresAt { get; set; }
    public bool HasPassword { get; set; }
    public bool AllowDownload { get; set; }
    public int? MaxViews { get; set; }
}

// ── Sent share links ──────────────────────────────────────────────────────────

public class SentShareLinkDto
{
    public string Token { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public bool HasPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
    public int ViewCount { get; set; }
    public Guid? AssetId { get; set; }
    public string? AssetFileName { get; set; }
    public string? AssetType { get; set; }
    public string? AssetThumbnailUrl { get; set; }
    public Guid? AlbumId { get; set; }
    public string? AlbumName { get; set; }
    public string? AlbumCoverUrl { get; set; }

    public string DisplayName => AssetFileName ?? AlbumName ?? "Contenido compartido";
    public string? ThumbnailUrl => AssetThumbnailUrl ?? AlbumCoverUrl;
}
