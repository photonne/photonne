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
    Task<byte[]?> ReadFileBytesAsync(string relativePath);
    Task<string?> GetBlobUrlAsync(string relativePath);
    Task RevokeBlobUrlAsync(string url);
    Task<string?> ComputeChecksumAsync(string relativePath);
    Task ClearAsync();
}
