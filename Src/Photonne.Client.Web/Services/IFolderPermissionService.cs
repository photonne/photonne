namespace Photonne.Client.Web.Services;

public interface IFolderPermissionService
{
    Task<List<FolderPermissionDto>> GetFolderPermissionsAsync(Guid folderId);
    Task<FolderPermissionDto> SetFolderPermissionAsync(Guid folderId, SetFolderPermissionRequest request);
    Task DeleteFolderPermissionAsync(Guid folderId, Guid userId);
}

public class FolderPermissionDto
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    public DateTime GrantedAt { get; set; }
    public Guid? GrantedByUserId { get; set; }
}

public class SetFolderPermissionRequest
{
    public Guid UserId { get; set; }
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
}
