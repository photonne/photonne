using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class Setting
{
    // Guid.Empty = global server setting; specific Guid = user-specific setting
    public Guid OwnerId { get; set; }

    [MaxLength(100)]
    public string Key { get; set; } = string.Empty;

    // No length limit — some values may contain JSON or long paths
    public string Value { get; set; } = string.Empty;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
