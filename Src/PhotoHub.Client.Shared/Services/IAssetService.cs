using PhotoHub.Client.Shared.Models;

namespace PhotoHub.Client.Shared.Services;

public interface IAssetService
{
    Task<TimelinePageResult> GetTimelinePageAsync(DateTime? cursor = null, int pageSize = 150);
    Task<List<TimelineItem>> GetDeviceAssetsAsync();
    Task<TimelineItem?> GetAssetByIdAsync(Guid id);
    Task<AssetDetail?> GetAssetDetailAsync(Guid id);
    Task<AssetDetail?> GetPendingAssetDetailAsync(string path);
    Task<List<TimelineItem>> GetAssetsByFolderAsync(Guid? folderId);
    Task<UploadResponse?> UploadAssetAsync(string fileName, Stream content, CancellationToken cancellationToken = default);
    Task<SyncAssetResponse?> SyncAssetAsync(string path, CancellationToken cancellationToken = default);
    IAsyncEnumerable<SyncProgressUpdate> SyncMultipleAssetsAsync(IEnumerable<string> paths, CancellationToken cancellationToken = default);
    Task DeleteAssetsAsync(DeleteAssetsRequest request);
    Task RestoreAssetsAsync(RestoreAssetsRequest request);
    Task PurgeAssetsAsync(PurgeAssetsRequest request);
    Task RestoreTrashAsync();
    Task EmptyTrashAsync();
    Task<List<string>> AddAssetTagsAsync(Guid assetId, List<string> tags);
    Task<List<string>> RemoveAssetTagAsync(Guid assetId, string tag);
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

