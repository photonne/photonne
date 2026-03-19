namespace PhotoHub.Client.Shared.Models;

public class AssetDetail
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public DateTime CreatedDate { get; set; }
    public DateTime ModifiedDate { get; set; }
    public string Extension { get; set; } = string.Empty;
    public DateTime ScannedAt { get; set; }
    public string Type { get; set; } = string.Empty;
    public string Checksum { get; set; } = string.Empty;
    public bool HasExif { get; set; }
    public bool HasThumbnails { get; set; }
    public Guid? FolderId { get; set; }
    public string? FolderPath { get; set; }
    public ExifData? Exif { get; set; }
    public List<ThumbnailInfo> Thumbnails { get; set; } = new();
    public List<string> Tags { get; set; } = new();
    public AssetSyncStatus SyncStatus { get; set; } = AssetSyncStatus.Pending;
    public bool IsFavorite { get; set; }
    public bool IsArchived { get; set; }
    public string? Description { get; set; }

    public string ThumbnailUrl => Id != Guid.Empty
        ? $"/api/assets/{Id}/thumbnail?size=Large" 
        : $"/api/assets/pending/content?path={System.Net.WebUtility.UrlEncode(FullPath)}";
    public string ContentUrl => Id != Guid.Empty
        ? $"/api/assets/{Id}/content" 
        : $"/api/assets/pending/content?path={System.Net.WebUtility.UrlEncode(FullPath)}";
    public string DisplayDate => CreatedDate.ToString("dd MMM yyyy HH:mm");
    public string FileSizeFormatted => FormatFileSize(FileSize);
    
    private static string FormatFileSize(long bytes)
    {
        string[] sizes = { "B", "KB", "MB", "GB" };
        double len = bytes;
        int order = 0;
        while (len >= 1024 && order < sizes.Length - 1)
        {
            order++;
            len = len / 1024;
        }
        return $"{len:0.##} {sizes[order]}";
    }
}

public class ExifData
{
    public DateTime? DateTaken { get; set; }
    public string? CameraMake { get; set; }
    public string? CameraModel { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public int? Orientation { get; set; }
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public double? Altitude { get; set; }
    public int? Iso { get; set; }
    public double? Aperture { get; set; }
    public double? ShutterSpeed { get; set; }
    public double? FocalLength { get; set; }
    public string? Description { get; set; }
    public string? Keywords { get; set; }
    public string? Software { get; set; }
}

public class ThumbnailInfo
{
    public Guid Id { get; set; }
    public string Size { get; set; } = string.Empty;
    public int Width { get; set; }
    public int Height { get; set; }
    public string Url => $"/api/assets/{AssetId}/thumbnail?size={Size}";
    public Guid AssetId { get; set; }
}
