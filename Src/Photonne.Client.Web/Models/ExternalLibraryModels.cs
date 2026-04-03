namespace Photonne.Client.Web.Models;

public class ExternalLibraryDto
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public bool ImportSubfolders { get; set; }
    public string? CronSchedule { get; set; }
    public DateTime? LastScannedAt { get; set; }
    public string LastScanStatus { get; set; } = "Idle";
    public int? LastScanAssetsFound { get; set; }
    public int? LastScanAssetsAdded { get; set; }
    public int? LastScanAssetsRemoved { get; set; }
    public int AssetCount { get; set; }
    public DateTime CreatedAt { get; set; }
}

public class LibraryScanProgressUpdate
{
    public string Message { get; set; } = string.Empty;
    public int Percentage { get; set; }
    public int AssetsFound { get; set; }
    public int AssetsIndexed { get; set; }
    public int AssetsMarkedOffline { get; set; }
    public bool IsCompleted { get; set; }
    public string? Error { get; set; }
}

public class CreateExternalLibraryRequest
{
    public string Name { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public bool ImportSubfolders { get; set; } = true;
    public string? CronSchedule { get; set; }
}

public class UpdateExternalLibraryRequest
{
    public string Name { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public bool ImportSubfolders { get; set; } = true;
    public string? CronSchedule { get; set; }
}
