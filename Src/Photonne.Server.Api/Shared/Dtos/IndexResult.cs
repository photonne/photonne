namespace Photonne.Server.Api.Shared.Dtos;

public class IndexResult
{
    public IndexStatistics Statistics { get; set; } = new();
    public int AssetsProcessed { get; set; }
    public string Message { get; set; } = string.Empty;
}

public class IndexStatistics
{
    public int TotalFilesFound { get; set; }
    public int NewFiles { get; set; }
    public int UpdatedFiles { get; set; }
    public int MovedFiles { get; set; }
    public int SkippedUnchanged { get; set; }
    public int OrphanedFilesRemoved { get; set; }
    public int OrphanedFoldersRemoved { get; set; }
    public int HashesCalculated { get; set; }
    public int ExifExtracted { get; set; }
    public int MediaTagsDetected { get; set; }
    public int MlJobsQueued { get; set; }
    public int ThumbnailsGenerated { get; set; }
    public int ThumbnailsRegenerated { get; set; }
    public int DuplicateAssetsRemoved { get; set; }
    public DateTime IndexCompletedAt { get; set; }
    public TimeSpan IndexDuration { get; set; }
}
