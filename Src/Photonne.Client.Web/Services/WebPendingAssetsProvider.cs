using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class WebPendingAssetsProvider : IPendingAssetsProvider
{
    private readonly ILocalFolderService _localFolderService;

    public WebPendingAssetsProvider(ILocalFolderService localFolderService)
    {
        _localFolderService = localFolderService;
    }

    public async Task<List<TimelineItem>> GetPendingAssetsAsync()
    {
        var files = await _localFolderService.EnumerateFilesAsync();

        return files.Select(f => new TimelineItem
        {
            Id = Guid.Empty,
            FileName = f.Name,
            FullPath = f.RelativePath,
            FileSize = f.Size,
            FileCreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(f.LastModified).UtcDateTime,
            FileModifiedAt = DateTimeOffset.FromUnixTimeMilliseconds(f.LastModified).UtcDateTime,
            Extension = Path.GetExtension(f.Name).TrimStart('.').ToLower(),
            Type = f.IsImage ? "Image" : "Video",
            SyncStatus = AssetSyncStatus.Pending,
            LocalThumbnailUrl = f.ThumbnailUrl,
            IsLocalOnly = true
        }).ToList();
    }

    public Task<AssetDetail?> GetPendingAssetDetailAsync(string path)
    {
        // Los assets locales no tienen detalle servidor; devuelve null.
        return Task.FromResult<AssetDetail?>(null);
    }
}
