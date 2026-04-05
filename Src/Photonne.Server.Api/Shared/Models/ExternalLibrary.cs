using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public enum ExternalLibraryScanStatus
{
    Idle,
    Running,
    Completed,
    Failed
}

public class ExternalLibrary
{
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(200)]
    public string Name { get; set; } = string.Empty;

    [Required]
    [MaxLength(1000)]
    public string Path { get; set; } = string.Empty;

    public bool ImportSubfolders { get; set; } = true;

    // Cron expression (e.g. "0 2 * * *" = every day at 2am). Null = manual only.
    [MaxLength(100)]
    public string? CronSchedule { get; set; }

    public DateTime? LastScannedAt { get; set; }

    public ExternalLibraryScanStatus LastScanStatus { get; set; } = ExternalLibraryScanStatus.Idle;

    public int? LastScanAssetsFound { get; set; }

    public int? LastScanAssetsAdded { get; set; }

    public int? LastScanAssetsRemoved { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public Guid OwnerId { get; set; }
    public User Owner { get; set; } = null!;

    // Navigation properties
    public ICollection<Asset> Assets { get; set; } = new List<Asset>();
    public ICollection<ExternalLibraryPermission> Permissions { get; set; } = new List<ExternalLibraryPermission>();
}
