using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Photonne.Server.Api.Shared.Models;

public enum AssetType
{
    Image,
    Video
}

/// <summary>
/// Where <see cref="Asset.CapturedAt"/> came from. Writers may only overwrite
/// the date when their source ranks equal-or-higher than the stored one —
/// with one exception: NOTHING overwrites <see cref="Manual"/> except another
/// manual edit. This is what keeps user-set and inferred dates alive across
/// metadata re-extractions (which rebuild the EXIF row from the file and
/// would otherwise clobber CapturedAt with the filesystem fallback).
/// </summary>
public enum CaptureDateSource
{
    /// <summary>Filesystem timestamp placeholder seeded at index/upload time.</summary>
    FileSystem = 0,
    /// <summary>Inferred from the file name or folder path (date-restore task).
    /// Real EXIF data found later replaces an inference.</summary>
    Inferred = 1,
    /// <summary>EXIF DateTimeOriginal read from the file.</summary>
    Exif = 2,
    /// <summary>Explicitly set by the user via the edit-date UI.</summary>
    Manual = 3
}

public class Asset
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    [Required]
    [MaxLength(500)]
    public string FileName { get; set; } = string.Empty;
    
    [Required]
    [MaxLength(1000)]
    public string FullPath { get; set; } = string.Empty;
    
    public long FileSize { get; set; }
    
    [Required]
    [MaxLength(64)]
    public string Checksum { get; set; } = string.Empty; // SHA256 hash
    
    public AssetType Type { get; set; }
    
    // File system dates (from the physical file, not the app)
    public DateTime FileCreatedAt { get; set; }

    public DateTime FileModifiedAt { get; set; }

    // Display timestamp used by the timeline. Filled from EXIF DateTimeOriginal
    // when present, otherwise falls back to FileCreatedAt. Stored on the row
    // (rather than computed via Exif join) so the timeline index can stay on a
    // single column and ordering doesn't require joining AssetExif. The Linux
    // filesystem rewrites mtime/ctime when assets are moved between volumes,
    // so FileCreatedAt is unreliable for chronological display. Column type
    // matches FileCreatedAt / Exif.DateTimeOriginal (no timezone) so backfills
    // and ORDER BY comparisons don't need a cast.
    [Column(TypeName = "timestamp without time zone")]
    public DateTime CapturedAt { get; set; }

    // Provenance of CapturedAt — gates who may overwrite it (see the enum).
    public CaptureDateSource CapturedAtSource { get; set; } = CaptureDateSource.FileSystem;

    // Older of FileCreatedAt/FileModifiedAt — the truthful "file existed by
    // then" timestamp. Linux hosts rewrite the birthtime when files are copied
    // between volumes (rsync preserves only mtime), so a creation date newer
    // than the modification date is always bogus. Same guard as
    // DirectoryScanner. Used as the CapturedAt fallback when EXIF has no date.
    [NotMapped]
    public DateTime EffectiveFileCreatedAt =>
        FileCreatedAt <= FileModifiedAt ? FileCreatedAt : FileModifiedAt;

    /// <summary>
    /// True when a writer with the given <paramref name="source"/> may
    /// overwrite <see cref="CapturedAt"/>: equal-or-higher rank wins, and
    /// Manual is only ever replaced by another manual edit.
    /// </summary>
    public bool CanOverwriteCapturedAt(CaptureDateSource source) =>
        CapturedAtSource == CaptureDateSource.Manual
            ? source == CaptureDateSource.Manual
            : source >= CapturedAtSource;

    [Required]
    [MaxLength(10)]
    public string Extension { get; set; } = string.Empty;
    
    public DateTime ScannedAt { get; set; } = DateTime.UtcNow;
    
    public Guid? OwnerId { get; set; }
    public User? Owner { get; set; }

    public Guid? FolderId { get; set; }
    public Folder? Folder { get; set; }

    // External library support. Null = asset uploaded normally (managed by Photonne).
    public Guid? ExternalLibraryId { get; set; }
    public ExternalLibrary? ExternalLibrary { get; set; }

    // True when the file no longer exists at FullPath (detected during library scan).
    public bool IsFileMissing { get; set; }

    public bool IsFavorite { get; set; }

    public bool IsArchived { get; set; }

    public DateTime? DeletedAt { get; set; }
    [MaxLength(1000)]
    public string? DeletedFromPath { get; set; }
    public Guid? DeletedFromFolderId { get; set; }
    
    // For videos
    public TimeSpan? Duration { get; set; }

    // User-defined caption (manually written by the user)
    [MaxLength(2000)]
    public string? Caption { get; set; }

    // AI-generated scene description
    [MaxLength(2000)]
    public string? AiDescription { get; set; }

    // Marks the most recent successful face recognition run on this asset. Used
    // by the backfill admin endpoint to skip already-processed assets.
    public DateTime? FaceRecognitionCompletedAt { get; set; }

    // Navigation properties
    public AssetExif? Exif { get; set; }
    public ICollection<AssetThumbnail> Thumbnails { get; set; } = new List<AssetThumbnail>();
    public ICollection<AssetTag> Tags { get; set; } = new List<AssetTag>();
    public ICollection<AssetUserTag> UserTags { get; set; } = new List<AssetUserTag>();
    public ICollection<AssetEnrichmentTask> EnrichmentTasks { get; set; } = new List<AssetEnrichmentTask>();
    public ICollection<Face> Faces { get; set; } = new List<Face>();
    public ICollection<AssetDetectedObject> DetectedObjects { get; set; } = new List<AssetDetectedObject>();
    public ICollection<AssetClassifiedScene> ClassifiedScenes { get; set; } = new List<AssetClassifiedScene>();
    public ICollection<AssetRecognizedTextLine> RecognizedTextLines { get; set; } = new List<AssetRecognizedTextLine>();

    // Marks the most recent successful object detection run. Used by the
    // backfill admin endpoint to skip already-processed assets.
    public DateTime? ObjectDetectionCompletedAt { get; set; }

    // Marks the most recent successful scene classification run. Used by the
    // backfill admin endpoint to skip already-processed assets.
    public DateTime? SceneClassificationCompletedAt { get; set; }

    // Marks the most recent successful OCR run. Used by the backfill admin
    // endpoint to skip already-processed assets.
    public DateTime? TextRecognitionCompletedAt { get; set; }

    // Marks the most recent successful CLIP image embedding run. Used by the
    // backfill admin endpoint to skip already-processed assets.
    public DateTime? ImageEmbeddingCompletedAt { get; set; }

    public AssetEmbedding? Embedding { get; set; }
}

