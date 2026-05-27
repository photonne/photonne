using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Photonne.Server.Api.Shared.Models;

public enum AssetType
{
    Image,
    Video
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

