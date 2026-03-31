namespace PhotoHub.Server.Api.Features.Map;

public class MapPointResponse
{
    public Guid Id { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public bool HasThumbnail { get; set; }
    public DateTime Date { get; set; }
}
