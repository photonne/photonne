using Microsoft.JSInterop;

namespace PhotoHub.Client.Web.Services;

public record LocalFileInfo(
    string Name,
    string RelativePath,
    long Size,
    long LastModified,   // Unix ms timestamp
    bool IsImage,
    string? ThumbnailUrl // blob: URL para imágenes, null para vídeos o HEIC
);

public interface ILocalFolderService
{
    Task<bool> IsSupportedAsync();
    Task<string?> PickFolderAsync();
    Task<string?> GetStoredFolderNameAsync();
    Task<bool> RequestPermissionAsync();
    Task<List<LocalFileInfo>> EnumerateFilesAsync();
    Task StartProgressiveEnumerationAsync<T>(DotNetObjectReference<T> dotNetRef, int batchSize = 20) where T : class;
    Task<List<LocalFileInfo>?> LoadMetadataCacheAsync(string folderName);
    Task SaveMetadataCacheAsync(string folderName, IEnumerable<LocalFileInfo> files);
    Task<Dictionary<string, string>> GetBlobUrlsBatchAsync(IEnumerable<string> relativePaths);
    Task<string?> GetBlobUrlAsync(string relativePath);
    Task RevokeBlobUrlAsync(string url);
    Task<byte[]?> ReadFileBytesAsync(string relativePath);
    Task<string?> ComputeChecksumAsync(string relativePath);
    Task ClearAsync();
}
