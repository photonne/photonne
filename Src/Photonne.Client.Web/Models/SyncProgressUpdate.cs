namespace Photonne.Client.Web.Models;

public class SyncProgressUpdate
{
    public int Current { get; set; }
    public int Total { get; set; }
    public int Successful { get; set; }
    public int Failed { get; set; }
    public string CurrentPath { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public bool IsCompleted { get; set; }
    public double Percentage => Total > 0 ? (double)Current / Total * 100 : 0;
}
