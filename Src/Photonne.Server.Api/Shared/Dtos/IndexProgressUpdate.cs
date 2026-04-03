namespace Photonne.Server.Api.Shared.Dtos;

public class IndexProgressUpdate
{
    public string Message { get; set; } = string.Empty;
    public double Percentage { get; set; }
    public IndexStatistics? Statistics { get; set; }
    public bool IsCompleted { get; set; }
}
