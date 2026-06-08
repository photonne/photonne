using System.Linq.Expressions;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class MediaRecognitionService
{
    /// <summary>
    /// Detects media type tags based on EXIF metadata and file characteristics
    /// </summary>
    // Sibling extensions used for Live Photo pairing. Matched case-insensitively
    // against the on-disk directory listing so a `.MOV` next to a `.HEIC` pairs
    // correctly even on a case-sensitive host filesystem (Linux).
    private static readonly string[] MotionSiblingExtensions = { ".mov", ".mp4", ".qt" };
    private static readonly string[] StillSiblingExtensions =
        { ".heic", ".heif", ".jpg", ".jpeg", ".png", ".dng", ".tiff", ".tif" };

    public async Task<List<AssetTagType>> DetectMediaTypeAsync(
        string filePath,
        AssetExif? exif,
        CancellationToken cancellationToken = default)
    {
        var tags = new List<AssetTagType>();

        // Live Photo pairing is independent of EXIF — it only depends on whether
        // a sibling motion/still file exists on disk — so it must run BEFORE the
        // `exif == null` guard below. (A still whose EXIF failed to extract would
        // otherwise never be tagged, and the .mov never gets MotionPhotoPart.)
        var ext = Path.GetExtension(filePath).ToLowerInvariant();
        var isStill = StillSiblingExtensions.Contains(ext);
        var isMotion = MotionSiblingExtensions.Contains(ext);

        if (isStill && HasSibling(filePath, MotionSiblingExtensions))
        {
            // A still with a paired motion clip — the Live Photo the client
            // animates on press-and-hold.
            tags.Add(AssetTagType.LivePhoto);
        }
        else if (isMotion && HasSibling(filePath, StillSiblingExtensions))
        {
            // The motion half of a Live Photo. Tagged so the timeline can hide
            // it: it should never appear as a standalone video next to its still.
            tags.Add(AssetTagType.MotionPhotoPart);
        }
        else if (EmbeddedMotionPhotoExtractor.IsCandidateExtension(filePath)
                 && EmbeddedMotionPhotoExtractor.IsEmbeddedMotionPhoto(filePath))
        {
            // Samsung/Google "motion photo": a single JPEG with the clip embedded
            // inside it (no sibling). Tag it LivePhoto too so the client shows the
            // same "play motion" affordance; the /motion endpoint extracts the
            // embedded MP4 on demand.
            tags.Add(AssetTagType.LivePhoto);
        }

        // Detect Burst (multiple files with same base name pattern)
        if (await IsBurstPhotoAsync(filePath, cancellationToken))
        {
            tags.Add(AssetTagType.Burst);
        }

        // Everything below needs EXIF dimensions/metadata.
        if (exif == null)
            return tags;

        // Detect Panorama (very wide or very tall aspect ratio)
        if (exif.Width.HasValue && exif.Height.HasValue)
        {
            var aspectRatio = (double)exif.Width.Value / exif.Height.Value;
            if (aspectRatio > 2.5 || aspectRatio < 0.4) // Very wide or very tall
            {
                tags.Add(AssetTagType.Panorama);
            }
        }

        // Detect Screenshot (common screen resolutions)
        if (IsScreenshot(exif))
        {
            tags.Add(AssetTagType.Screenshot);
        }

        // Detect HDR (specific metadata)
        if (IsHDR(exif))
        {
            tags.Add(AssetTagType.HDR);
        }

        return tags;
    }

    /// <summary>
    /// True when a file sharing <paramref name="filePath"/>'s base name but with
    /// one of <paramref name="extensions"/> exists in the same directory. Matches
    /// case-insensitively on both base name and extension by listing the
    /// directory once, so pairing survives a case-sensitive filesystem (a `.MOV`
    /// recorded by iOS next to an `IMG_1234.HEIC`).
    /// </summary>
    private static bool HasSibling(string filePath, string[] extensions)
    {
        var directory = Path.GetDirectoryName(filePath);
        if (string.IsNullOrEmpty(directory) || !Directory.Exists(directory))
            return false;

        var baseName = Path.GetFileNameWithoutExtension(filePath);

        foreach (var sibling in Directory.EnumerateFiles(directory))
        {
            if (string.Equals(sibling, filePath, StringComparison.OrdinalIgnoreCase))
                continue;

            if (!string.Equals(Path.GetFileNameWithoutExtension(sibling), baseName,
                    StringComparison.OrdinalIgnoreCase))
                continue;

            var siblingExt = Path.GetExtension(sibling).ToLowerInvariant();
            if (extensions.Contains(siblingExt))
                return true;
        }

        return false;
    }
    
    private bool IsScreenshot(AssetExif exif)
    {
        // Common screenshot resolutions
        var commonScreenshotResolutions = new[]
        {
            (1920, 1080), (2560, 1440), (3840, 2160), // 16:9
            (1080, 1920), (1440, 2560), (2160, 3840), // 9:16 (vertical)
            (2048, 1536), (1024, 768) // 4:3
        };
        
        if (exif.Width.HasValue && exif.Height.HasValue)
        {
            return commonScreenshotResolutions.Contains(
                (exif.Width.Value, exif.Height.Value));
        }
        
        return false;
    }
    
    private async Task<bool> IsBurstPhotoAsync(string filePath, CancellationToken cancellationToken)
    {
        // Burst photos: files with format "IMG_1234_001.jpg", "IMG_1234_002.jpg", etc.
        var fileName = Path.GetFileNameWithoutExtension(filePath);
        var pattern = @"^(.+)_\d{3,}$";
        
        return await Task.Run(() => 
            System.Text.RegularExpressions.Regex.IsMatch(fileName, pattern), 
            cancellationToken);
    }
    
    private bool IsHDR(AssetExif exif)
    {
        // Look for HDR in keywords or description
        var hdrKeywords = new[] { "HDR", "High Dynamic Range" };
        return exif.Keywords != null && 
               hdrKeywords.Any(k => exif.Keywords.Contains(k, StringComparison.OrdinalIgnoreCase));
    }
    
    /// <summary>
    /// Determines if ML jobs should be triggered for this asset
    /// </summary>
    public bool ShouldTriggerMlJob(Asset asset, AssetExif? exif)
    {
        // Decide based on:
        // - Asset type (only images)
        // - Size (large images)
        // - Should not have pending jobs already
        return asset.Type == AssetType.Image &&
               exif != null &&
               exif.Width.HasValue &&
               exif.Height.HasValue &&
               (exif.Width.Value * exif.Height.Value) > 500000; // > 500K pixels
    }

    public static readonly IReadOnlyList<AssetEnrichmentType> AllMlTaskTypes = new[]
    {
        AssetEnrichmentType.FaceRecognition,
        AssetEnrichmentType.ObjectDetection,
        AssetEnrichmentType.SceneClassification,
        AssetEnrichmentType.TextRecognition,
        AssetEnrichmentType.ImageEmbedding,
    };

    /// <summary>
    /// Returns the ML enrichment task types that should be enqueued for an
    /// asset: gated by <see cref="ShouldTriggerMlJob"/> and excluding types
    /// whose corresponding *CompletedAt timestamp is already set (previously
    /// processed successfully). Pending/Processing duplicates are filtered
    /// later by <see cref="IEnrichmentService.EnqueueAsync"/>.
    /// </summary>
    public IReadOnlyList<AssetEnrichmentType> GetMissingMlTaskTypes(Asset asset, AssetExif? exif)
    {
        if (!ShouldTriggerMlJob(asset, exif))
            return Array.Empty<AssetEnrichmentType>();

        var missing = new List<AssetEnrichmentType>(AllMlTaskTypes.Count);
        if (asset.FaceRecognitionCompletedAt == null) missing.Add(AssetEnrichmentType.FaceRecognition);
        if (asset.ObjectDetectionCompletedAt == null) missing.Add(AssetEnrichmentType.ObjectDetection);
        if (asset.SceneClassificationCompletedAt == null) missing.Add(AssetEnrichmentType.SceneClassification);
        if (asset.TextRecognitionCompletedAt == null) missing.Add(AssetEnrichmentType.TextRecognition);
        if (asset.ImageEmbeddingCompletedAt == null) missing.Add(AssetEnrichmentType.ImageEmbedding);
        return missing;
    }

    /// <summary>
    /// EF-translatable predicate matching assets that have not yet completed
    /// the given ML task type. Shared by the admin backfill endpoints.
    /// </summary>
    public static Expression<Func<Asset, bool>> MissingCompletionFilter(AssetEnrichmentType taskType) => taskType switch
    {
        AssetEnrichmentType.FaceRecognition     => a => a.FaceRecognitionCompletedAt == null,
        AssetEnrichmentType.ObjectDetection     => a => a.ObjectDetectionCompletedAt == null,
        AssetEnrichmentType.SceneClassification => a => a.SceneClassificationCompletedAt == null,
        AssetEnrichmentType.TextRecognition     => a => a.TextRecognitionCompletedAt == null,
        AssetEnrichmentType.ImageEmbedding      => a => a.ImageEmbeddingCompletedAt == null,
        _ => throw new ArgumentOutOfRangeException(nameof(taskType), taskType, "Not an ML task type"),
    };

    /// <summary>
    /// EF-translatable predicate matching assets that have already completed
    /// the given ML task type. Used by the admin pending-count endpoint to
    /// surface a global "done" counter alongside `unprocessed` / `inQueue`,
    /// so the Run Tasks hub can render an honest progress bar instead of an
    /// indeterminate one for AI rows.
    /// </summary>
    public static Expression<Func<Asset, bool>> CompletedFilter(AssetEnrichmentType taskType) => taskType switch
    {
        AssetEnrichmentType.FaceRecognition     => a => a.FaceRecognitionCompletedAt != null,
        AssetEnrichmentType.ObjectDetection     => a => a.ObjectDetectionCompletedAt != null,
        AssetEnrichmentType.SceneClassification => a => a.SceneClassificationCompletedAt != null,
        AssetEnrichmentType.TextRecognition     => a => a.TextRecognitionCompletedAt != null,
        AssetEnrichmentType.ImageEmbedding      => a => a.ImageEmbeddingCompletedAt != null,
        _ => throw new ArgumentOutOfRangeException(nameof(taskType), taskType, "Not an ML task type"),
    };
}
