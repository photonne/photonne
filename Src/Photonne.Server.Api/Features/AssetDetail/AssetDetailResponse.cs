namespace Photonne.Server.Api.Features.AssetDetail;

public class AssetDetailResponse
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public DateTime FileCreatedAt { get; set; }
    public DateTime FileModifiedAt { get; set; }
    public string Extension { get; set; } = string.Empty;
    public DateTime ScannedAt { get; set; }
    public string Type { get; set; } = string.Empty;
    public string Checksum { get; set; } = string.Empty;
    public bool HasExif { get; set; }
    public bool HasThumbnails { get; set; }
    public Guid? FolderId { get; set; }
    public string? FolderPath { get; set; }
    public ExifDataResponse? Exif { get; set; }
    public List<ThumbnailInfoResponse> Thumbnails { get; set; } = new();
    public List<string> Tags { get; set; } = new();
    public Photonne.Server.Api.Shared.Dtos.AssetSyncStatus SyncStatus { get; set; } = Photonne.Server.Api.Shared.Dtos.AssetSyncStatus.Synced;
    public bool IsFavorite { get; set; }
    public bool IsArchived { get; set; }
    public bool IsFileMissing { get; set; }
    public string? Caption { get; set; }
    public string? AiDescription { get; set; }
    public bool IsReadOnly { get; set; }
}

public class ExifDataResponse
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

public class ThumbnailInfoResponse
{
    public Guid Id { get; set; }
    public string Size { get; set; } = string.Empty;
    public int Width { get; set; }
    public int Height { get; set; }
    public Guid AssetId { get; set; }
}
