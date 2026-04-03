using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class FolderPermission
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    [Required]
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;
    
    [Required]
    public Guid FolderId { get; set; }
    public Folder Folder { get; set; } = null!;
    
    // Permission flags
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    
    public DateTime GrantedAt { get; set; } = DateTime.UtcNow;
    
    public Guid? GrantedByUserId { get; set; }
    public User? GrantedByUser { get; set; }
}

