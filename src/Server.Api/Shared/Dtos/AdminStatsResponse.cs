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

/// <summary>One month's worth of the library, used by the admin growth
/// chart. Buckets are keyed by capture date so the chart mirrors how the
/// timeline fills up over time.</summary>
public class MonthlyGrowthPoint
{
    public int Year { get; set; }
    public int Month { get; set; }
    public int Photos { get; set; }
    public int Videos { get; set; }
}
