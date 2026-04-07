using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IFolderService
{
    Task<List<FolderItem>> GetFoldersAsync();
    Task<FolderItem?> GetFolderByIdAsync(Guid id);
    Task<List<FolderItem>> GetFolderTreeAsync();
    Task<List<FolderItem>> GetMyFolderTreeAsync();
    Task<List<TimelineItem>> GetFolderAssetsAsync(Guid folderId);
    Task<FolderItem> CreateFolderAsync(CreateFolderRequest request);
    Task<FolderItem> UpdateFolderAsync(Guid folderId, UpdateFolderRequest request);
    Task DeleteFolderAsync(Guid folderId);
    Task MoveFolderAssetsAsync(MoveFolderAssetsRequest request);
    Task RemoveFolderAssetsAsync(RemoveFolderAssetsRequest request);
    Task<FolderItem?> GetLibraryRootFolderAsync(Guid libraryId);
}
