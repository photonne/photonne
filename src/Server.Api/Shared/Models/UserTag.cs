using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class UserTag
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid OwnerId { get; set; }
    public User Owner { get; set; } = null!;

    [Required]
    [MaxLength(80)]
    public string Name { get; set; } = string.Empty;

    [Required]
    [MaxLength(80)]
    public string NormalizedName { get; set; } = string.Empty;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<AssetUserTag> AssetLinks { get; set; } = new List<AssetUserTag>();
}
