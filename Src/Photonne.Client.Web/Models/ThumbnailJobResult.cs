namespace Photonne.Client.Web.Models;

public class ThumbnailProgressUpdate
{
    public string Message { get; set; } = string.Empty;
    public double Percentage { get; set; }
    public ThumbnailJobStatistics? Statistics { get; set; }
    public bool IsCompleted { get; set; }
    public Guid? TaskId { get; set; }
}

public class ThumbnailJobStatistics
{
    public int TotalAssets { get; set; }
    public int Processed { get; set; }
    public int Generated { get; set; }
    public int Skipped { get; set; }
    public int Failed { get; set; }
}

// Per-line JSON body streamed by the admin maintenance tasks
// (GET /api/admin/maintenance/{kind}/stream and .../face-clustering/stream).
// Shape mirrors ThumbnailProgressUpdate so the Native client reuses the same
// NDJSON plumbing; Processed/Affected carry the running counts that used to
// come back only in the final MaintenanceTaskResult.
public class MaintenanceProgressUpdate
{
    public string Message { get; set; } = string.Empty;
    public double Percentage { get; set; }
    public int Processed { get; set; }
    public int Affected { get; set; }
    public bool IsCompleted { get; set; }
    public Guid? TaskId { get; set; }
}
