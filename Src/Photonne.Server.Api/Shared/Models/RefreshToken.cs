using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class RefreshToken
{
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(256)]
    public string TokenHash { get; set; } = string.Empty;

    [Required]
    [MaxLength(200)]
    public string DeviceId { get; set; } = string.Empty;

    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime ExpiresAt { get; set; }
    public DateTime? RevokedAt { get; set; }
}
