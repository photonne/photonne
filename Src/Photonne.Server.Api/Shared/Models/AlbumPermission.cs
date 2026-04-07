namespace Photonne.Server.Api.Shared.Models;

public class AlbumPermission
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    public Guid AlbumId { get; set; }
    public Album Album { get; set; } = null!;
    
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;
    
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    
    public DateTime GrantedAt { get; set; } = DateTime.UtcNow;
    
    public Guid? GrantedByUserId { get; set; }
    public User? GrantedByUser { get; set; }
}
