namespace PhotoHub.Client.Shared.Models;

public class TimelineItem
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
    public AssetSyncStatus SyncStatus { get; set; } = AssetSyncStatus.Synced;
    public DateTime? DeletedAt { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public List<string> Tags { get; set; } = new();
    public bool IsFavorite { get; set; }
    public bool IsArchived { get; set; }

    public double AspectRatio => Width.HasValue && Height.HasValue && Height.Value > 0 
        ? (double)Width.Value / Height.Value 
        : 1.0;
    
    public string ThumbnailUrl => SyncStatus == AssetSyncStatus.Synced 
        ? $"/api/assets/{Id}/thumbnail?size=Medium" 
        : $"/api/assets/pending/thumbnail?path={System.Net.WebUtility.UrlEncode(FullPath)}&size=Medium";
    public string ContentUrl => SyncStatus == AssetSyncStatus.Synced 
        ? $"/api/assets/{Id}/content" 
        : $"/api/assets/pending/content?path={System.Net.WebUtility.UrlEncode(FullPath)}";
    public string DisplayDate => CreatedDate.ToString("dd MMM yyyy");
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
