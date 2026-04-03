namespace Photonne.Client.Web.Services;

public interface IAlbumPermissionService
{
    Task<List<AlbumPermissionDto>> GetAlbumPermissionsAsync(Guid albumId);
    Task<AlbumPermissionDto> SetAlbumPermissionAsync(Guid albumId, SetAlbumPermissionRequest request);
    Task DeleteAlbumPermissionAsync(Guid albumId, Guid userId);
}

public class AlbumPermissionDto
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public bool CanView { get; set; }
    public bool CanEdit { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    public DateTime GrantedAt { get; set; }
    public Guid? GrantedByUserId { get; set; }
}

public class SetAlbumPermissionRequest
{
    public Guid UserId { get; set; }
    public bool CanView { get; set; }
    public bool CanEdit { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
}
