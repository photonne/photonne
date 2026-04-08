using Photonne.Server.Api.Shared.Dtos;

namespace Photonne.Server.Api.Features.Timeline;

public class TimelineResponse
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public DateTime FileCreatedAt { get; set; }
    public DateTime FileModifiedAt { get; set; }
    public string Extension { get; set; } = string.Empty;
    public DateTime ScannedAt { get; set; }
    public string Type { get; set; } = string.Empty; // IMAGE or VIDEO
    public string Checksum { get; set; } = string.Empty;
    public bool HasExif { get; set; }
    public bool HasThumbnails { get; set; }
    public AssetSyncStatus SyncStatus { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public DateTime? DeletedAt { get; set; }
    public List<string> Tags { get; set; } = new();
    public bool IsFavorite { get; set; }
    public bool IsArchived { get; set; }
    public bool IsFileMissing { get; set; }
}

