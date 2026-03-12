using System.ComponentModel.DataAnnotations;

namespace PhotoHub.Server.Api.Shared.Models;

public class User
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    [Required]
    [MaxLength(100)]
    public string Username { get; set; } = string.Empty;
    
    [Required]
    [MaxLength(255)]
    public string Email { get; set; } = string.Empty;
    
    [Required]
    [MaxLength(255)]
    public string PasswordHash { get; set; } = string.Empty;
    
    [MaxLength(100)]
    public string? FirstName { get; set; }
    
    [MaxLength(100)]
    public string? LastName { get; set; }
    
    public bool IsActive { get; set; } = true;
    
    public bool IsEmailVerified { get; set; } = false;
    
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    
    public DateTime? LastLoginAt { get; set; }
    
    [MaxLength(50)]
    public string Role { get; set; } = "User";

    public long? StorageQuotaBytes { get; set; }

    // Navigation properties
    public ICollection<FolderPermission> FolderPermissions { get; set; } = new List<FolderPermission>();
    public ICollection<Asset> Assets { get; set; } = new List<Asset>();
    public ICollection<AlbumPermission> AlbumPermissions { get; set; } = new List<AlbumPermission>();
    public ICollection<Album> OwnedAlbums { get; set; } = new List<Album>();
    public ICollection<RefreshToken> RefreshTokens { get; set; } = new List<RefreshToken>();
    public ICollection<UserTag> Tags { get; set; } = new List<UserTag>();
}

