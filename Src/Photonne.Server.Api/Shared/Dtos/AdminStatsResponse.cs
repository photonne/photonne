namespace Photonne.Server.Api.Shared.Dtos;

public class AdminStatsResponse
{
    public int TotalPhotos { get; set; }
    public int TotalVideos { get; set; }
    public long TotalBytes { get; set; }
    public List<AdminUserUsage> Users { get; set; } = new();
}

public class AdminUserUsage
{
    public Guid UserId { get; set; }
    public string DisplayName { get; set; } = string.Empty;
    public string? Email { get; set; }
    public int Photos { get; set; }
    public int Videos { get; set; }
    public long PhotoBytes { get; set; }
    public long VideoBytes { get; set; }
}
