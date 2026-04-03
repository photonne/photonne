namespace Photonne.Client.Web.Models;

public class IndexProgressUpdate
{
    public string Message { get; set; } = string.Empty;
    public double Percentage { get; set; }
    public IndexStatistics? Statistics { get; set; }
    public bool IsCompleted { get; set; }
}
