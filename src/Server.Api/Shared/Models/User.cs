using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

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

    public bool IsPrimaryAdmin { get; set; } = false;

    // Two-step verification (TOTP). The secret is set while it's being set
    // up and only counts once TotpEnabled; the recovery codes are SHA-256
    // hashes separated by ';'; TotpLastStep stops a code being used twice.
    [MaxLength(64)]
    public string? TotpSecret { get; set; }

    public bool TotpEnabled { get; set; }

    [MaxLength(1000)]
    public string? TotpRecoveryCodes { get; set; }

    public long? TotpLastStep { get; set; }

    /// <summary>
    /// The OpenID provider's subject ("sub") of this account, once signed in
    /// through it: later sign-ins match on it, not on the email.
    /// </summary>
    [MaxLength(255)]
    public string? OidcSubject { get; set; }

    // Navigation properties
    public ICollection<FolderPermission> FolderPermissions { get; set; } = new List<FolderPermission>();
    public ICollection<Asset> Assets { get; set; } = new List<Asset>();
    public ICollection<AlbumPermission> AlbumPermissions { get; set; } = new List<AlbumPermission>();
    public ICollection<Album> OwnedAlbums { get; set; } = new List<Album>();
    public ICollection<RefreshToken> RefreshTokens { get; set; } = new List<RefreshToken>();
    public ICollection<UserTag> Tags { get; set; } = new List<UserTag>();
    public ICollection<ExternalLibrary> ExternalLibraries { get; set; } = new List<ExternalLibrary>();
    public ICollection<ExternalLibraryPermission> ExternalLibraryPermissions { get; set; } = new List<ExternalLibraryPermission>();
}

