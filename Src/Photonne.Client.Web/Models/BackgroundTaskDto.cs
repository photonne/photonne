namespace Photonne.Client.Web.Models;

public class BackgroundTaskDto
{
    public Guid Id { get; set; }
    public string Type { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public double Percentage { get; set; }
    public string LastMessage { get; set; } = string.Empty;
    public DateTime StartedAt { get; set; }
    public DateTime? FinishedAt { get; set; }
    public Dictionary<string, string> Parameters { get; set; } = new();

    public bool IsRunning => Status == "Running";
}
