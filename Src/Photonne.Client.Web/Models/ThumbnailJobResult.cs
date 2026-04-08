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
