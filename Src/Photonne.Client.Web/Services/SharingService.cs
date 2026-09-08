using System.Net.Http.Json;

namespace Photonne.Client.Web.Services;

public class ShareableUser
{
    public Guid Id { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
}

/// <summary>Fila de permisos: misma forma para álbum y carpeta en el servidor.</summary>
public class SharePermission
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
}

public class SetPermissionRequest
{
    public Guid UserId { get; set; }
    public bool CanRead { get; set; } = true;
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
}

public interface ISharingService
{
    Task<List<ShareableUser>> GetShareableUsersAsync();
    Task<List<SharePermission>> GetPermissionsAsync(string kind, Guid id);
    Task SetPermissionAsync(string kind, Guid id, SetPermissionRequest request);
    Task RemovePermissionAsync(string kind, Guid id, Guid userId);
}

/// <summary>
/// Compartición de álbumes y carpetas: los endpoints de permisos son gemelos
/// (/api/{albums|folders}/{id}/permissions), así que [kind] es "albums" o
/// "folders" y el resto es idéntico.
/// </summary>
public class SharingService : ISharingService
{
    private readonly HttpClient _httpClient;

    public SharingService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<List<ShareableUser>> GetShareableUsersAsync()
    {
        var response = await _httpClient.GetFromJsonAsync<List<ShareableUser>>("/api/users/shareable");
        return response ?? new List<ShareableUser>();
    }

    public async Task<List<SharePermission>> GetPermissionsAsync(string kind, Guid id)
    {
        var response = await _httpClient.GetFromJsonAsync<List<SharePermission>>(
            $"/api/{kind}/{id}/permissions");
        return response ?? new List<SharePermission>();
    }

    public async Task SetPermissionAsync(string kind, Guid id, SetPermissionRequest request)
    {
        var response = await _httpClient.PostAsJsonAsync($"/api/{kind}/{id}/permissions", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task RemovePermissionAsync(string kind, Guid id, Guid userId)
    {
        var response = await _httpClient.DeleteAsync($"/api/{kind}/{id}/permissions/{userId}");
        response.EnsureSuccessStatusCode();
    }
}
