namespace Photonne.Client.Web.Models;

public class TrashStatsResponse
{
    public int TotalItems { get; set; }
    public long TotalBytes { get; set; }
    public int ExpiredItems { get; set; }
    public int RetentionDays { get; set; }
}

public class TrashCleanupResult
{
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
    public int Deleted { get; set; }
}
