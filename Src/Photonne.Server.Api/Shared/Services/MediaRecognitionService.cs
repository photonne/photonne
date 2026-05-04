using System.Linq.Expressions;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class MediaRecognitionService
{
    /// <summary>
    /// Detects media type tags based on EXIF metadata and file characteristics
    /// </summary>
    public async Task<List<AssetTagType>> DetectMediaTypeAsync(
        string filePath, 
        AssetExif? exif, 
        CancellationToken cancellationToken = default)
    {
        var tags = new List<AssetTagType>();
        
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
        
        // Detect Live Photo (look for related .mov file)
        if (await IsLivePhotoAsync(filePath, cancellationToken))
        {
            tags.Add(AssetTagType.LivePhoto);
        }
        
        // Detect Burst (multiple files with same base name pattern)
        if (await IsBurstPhotoAsync(filePath, cancellationToken))
        {
            tags.Add(AssetTagType.Burst);
        }
        
        // Detect HDR (specific metadata)
        if (IsHDR(exif))
        {
            tags.Add(AssetTagType.HDR);
        }
        
        return tags;
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
    
    private async Task<bool> IsLivePhotoAsync(string filePath, CancellationToken cancellationToken)
    {
        // iOS Live Photos: look for .mov file with same base name
        var directory = Path.GetDirectoryName(filePath);
        if (string.IsNullOrEmpty(directory))
            return false;
            
        var fileNameWithoutExt = Path.GetFileNameWithoutExtension(filePath);
        var movPath = Path.Combine(directory, $"{fileNameWithoutExt}.mov");
        
        return await Task.Run(() => File.Exists(movPath), cancellationToken);
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

    public static readonly IReadOnlyList<MlJobType> AllJobTypes = new[]
    {
        MlJobType.FaceRecognition,
        MlJobType.ObjectDetection,
        MlJobType.SceneClassification,
        MlJobType.TextRecognition,
        MlJobType.ImageEmbedding,
    };

    /// <summary>
    /// Returns the ML job types that should be enqueued for an asset: gated by
    /// <see cref="ShouldTriggerMlJob"/>, and excluding types whose corresponding
    /// *CompletedAt timestamp is already set (i.e. previously processed
    /// successfully). Pending/Processing duplicates are filtered later by
    /// <see cref="IMlJobService.EnqueueMlJobAsync"/>.
    /// </summary>
    public IReadOnlyList<MlJobType> GetMissingMlJobTypes(Asset asset, AssetExif? exif)
    {
        if (!ShouldTriggerMlJob(asset, exif))
            return Array.Empty<MlJobType>();

        var missing = new List<MlJobType>(AllJobTypes.Count);
        if (asset.FaceRecognitionCompletedAt == null) missing.Add(MlJobType.FaceRecognition);
        if (asset.ObjectDetectionCompletedAt == null) missing.Add(MlJobType.ObjectDetection);
        if (asset.SceneClassificationCompletedAt == null) missing.Add(MlJobType.SceneClassification);
        if (asset.TextRecognitionCompletedAt == null) missing.Add(MlJobType.TextRecognition);
        if (asset.ImageEmbeddingCompletedAt == null) missing.Add(MlJobType.ImageEmbedding);
        return missing;
    }

    /// <summary>
    /// EF-translatable predicate matching assets that have not yet completed
    /// the given ML job type. Shared by the admin backfill endpoints.
    /// </summary>
    public static Expression<Func<Asset, bool>> MissingCompletionFilter(MlJobType jobType) => jobType switch
    {
        MlJobType.FaceRecognition => a => a.FaceRecognitionCompletedAt == null,
        MlJobType.ObjectDetection => a => a.ObjectDetectionCompletedAt == null,
        MlJobType.SceneClassification => a => a.SceneClassificationCompletedAt == null,
        MlJobType.TextRecognition => a => a.TextRecognitionCompletedAt == null,
        MlJobType.ImageEmbedding => a => a.ImageEmbeddingCompletedAt == null,
        _ => throw new ArgumentOutOfRangeException(nameof(jobType), jobType, null),
    };
}
