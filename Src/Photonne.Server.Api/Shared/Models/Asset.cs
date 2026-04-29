using System.ComponentModel.DataAnnotations;

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

    // Marks the most recent successful face detection run on this asset. Used
    // by the backfill admin endpoint to skip already-processed assets.
    public DateTime? FaceDetectionCompletedAt { get; set; }

    // Navigation properties
    public AssetExif? Exif { get; set; }
    public ICollection<AssetThumbnail> Thumbnails { get; set; } = new List<AssetThumbnail>();
    public ICollection<AssetTag> Tags { get; set; } = new List<AssetTag>();
    public ICollection<AssetUserTag> UserTags { get; set; } = new List<AssetUserTag>();
    public ICollection<AssetMlJob> MlJobs { get; set; } = new List<AssetMlJob>();
    public ICollection<Face> Faces { get; set; } = new List<Face>();
    public ICollection<ObjectDetection> ObjectDetections { get; set; } = new List<ObjectDetection>();
    public ICollection<SceneClassification> SceneClassifications { get; set; } = new List<SceneClassification>();

    // Marks the most recent successful object recognition run. Used by the
    // backfill admin endpoint to skip already-processed assets.
    public DateTime? ObjectRecognitionCompletedAt { get; set; }

    // Marks the most recent successful scene classification run. Used by the
    // backfill admin endpoint to skip already-processed assets.
    public DateTime? SceneClassificationCompletedAt { get; set; }
}

