using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IAssetService
{
    Task<TimelinePageResult> GetTimelinePageAsync(DateTime? cursor = null, int pageSize = 150);
    Task<TimelinePageResult> GetTimelineSectionAsync(DateTime from, DateTime to, int pageSize = 500, CancellationToken cancellationToken = default);
    Task<List<TimelineIndexItem>> GetTimelineIndexAsync();
    Task<List<TimelineItem>> GetDeviceAssetsAsync();
    Task<TimelineItem?> GetAssetByIdAsync(Guid id);
    Task<AssetDetail?> GetAssetDetailAsync(Guid id);
    Task<AssetDetail?> GetPendingAssetDetailAsync(string path);
    Task<List<TimelineItem>> GetAssetsByFolderAsync(Guid? folderId);
    Task<UploadResponse?> UploadAssetAsync(string fileName, Stream content, CancellationToken cancellationToken = default);
    Task<HashSet<string>> CheckExistingAsync(IEnumerable<(string Name, long Size)> files, CancellationToken cancellationToken = default);
    Task<Guid?> ExistsByChecksumAsync(string checksum, CancellationToken cancellationToken = default);
    Task<SyncAssetResponse?> SyncAssetAsync(string path, CancellationToken cancellationToken = default);
    IAsyncEnumerable<SyncProgressUpdate> SyncMultipleAssetsAsync(IEnumerable<string> paths, CancellationToken cancellationToken = default);
    Task DeleteAssetsAsync(DeleteAssetsRequest request);
    Task RestoreAssetsAsync(RestoreAssetsRequest request);
    Task PurgeAssetsAsync(PurgeAssetsRequest request);
    Task RestoreTrashAsync();
    Task EmptyTrashAsync();
    Task<List<string>> AddAssetTagsAsync(Guid assetId, List<string> tags);
    Task<List<string>> RemoveAssetTagAsync(Guid assetId, string tag);
    Task<(List<TimelineItem> Items, bool HasMore)> SearchAssetsAsync(string? q, DateTime? from, DateTime? to, string? folder, int pageSize = 100);
    Task<bool> ToggleFavoriteAsync(Guid assetId);
    Task<TimelinePageResult> GetFavoritesPageAsync(DateTime? cursor = null, int pageSize = 150);
    Task<List<TimelineItem>> GetMemoriesAsync(bool test = false);
    Task<byte[]?> DownloadZipAsync(List<Guid> assetIds, string? fileName = null);
    Task<TimelinePageResult> GetArchivedPageAsync(DateTime? cursor = null, int pageSize = 150);
    Task ArchiveAssetsAsync(ArchiveAssetsRequest request);
    Task UnarchiveAssetsAsync(UnarchiveAssetsRequest request);
    Task UnarchiveAllAsync();
    Task<List<UserDuplicateGroup>> GetMyDuplicatesAsync();
    Task<List<TimelineItem>> GetLargeFilesAsync(int count = 50);
    Task<string?> UpdateDescriptionAsync(Guid assetId, string? caption);
}

public class ArchiveAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class UnarchiveAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class SearchResult
{
    public List<TimelineItem> Items { get; set; } = new();
    public bool HasMore { get; set; }
}

public class FavoriteToggleResult
{
    public bool IsFavorite { get; set; }
}

public class UploadResponse
{
    public string Message { get; set; } = string.Empty;
    public Guid? AssetId { get; set; }
}

public class SyncAssetResponse
{
    public string Message { get; set; } = string.Empty;
    public Guid? AssetId { get; set; } // Opcional: solo se devuelve si el asset ya estaba indexado
    public string? TargetPath { get; set; } // Ruta donde se copió el archivo
}

