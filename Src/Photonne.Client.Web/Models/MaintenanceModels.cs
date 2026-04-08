namespace Photonne.Client.Web.Models;

public class MaintenanceTaskResult
{
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
    public int Processed { get; set; }
    public int Affected { get; set; }
}
