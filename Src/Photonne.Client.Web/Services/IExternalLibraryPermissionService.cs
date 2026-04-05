using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IExternalLibraryPermissionService
{
    Task<List<ExternalLibraryPermissionDto>> GetPermissionsAsync(Guid libraryId, CancellationToken ct = default);
    Task<ExternalLibraryPermissionDto?> SetPermissionAsync(Guid libraryId, SetExternalLibraryPermissionRequest request, CancellationToken ct = default);
    Task<bool> RemovePermissionAsync(Guid libraryId, Guid userId, CancellationToken ct = default);
}
