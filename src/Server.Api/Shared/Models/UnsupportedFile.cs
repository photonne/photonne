using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// A file found on disk during a scan whose extension isn't a recognised image
/// or video. These are catalogued separately from <see cref="Asset"/> so the
/// user can see everything that physically exists in their storage (and
/// download the original) without polluting the timeline or the Asset model.
/// No enrichment (EXIF / thumbnails / ML) is ever run on these.
/// </summary>
public class UnsupportedFile
{
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(500)]
    public string FileName { get; set; } = string.Empty;

    // Virtual/stored path (same convention as Asset.FullPath), unique.
    [Required]
    [MaxLength(1000)]
    public string FullPath { get; set; } = string.Empty;

    public long FileSize { get; set; }

    // Unsupported files can carry long/unknown extensions, so allow more room
    // than Asset.Extension (10).
    [MaxLength(40)]
    public string Extension { get; set; } = string.Empty;

    public DateTime FileCreatedAt { get; set; }
    public DateTime FileModifiedAt { get; set; }

    // When the scanner first catalogued this file. Used as the listing's cursor.
    public DateTime DiscoveredAt { get; set; } = DateTime.UtcNow;

    public Guid? OwnerId { get; set; }
    public User? Owner { get; set; }

    public Guid? FolderId { get; set; }
    public Folder? Folder { get; set; }

    // Non-null = discovered inside an external library.
    public Guid? ExternalLibraryId { get; set; }
    public ExternalLibrary? ExternalLibrary { get; set; }
}
