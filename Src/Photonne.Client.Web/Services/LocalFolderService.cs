using Microsoft.JSInterop;

namespace Photonne.Client.Web.Services;

public class LocalFolderService : ILocalFolderService
{
    private readonly IJSRuntime _js;

    public LocalFolderService(IJSRuntime js)
    {
        _js = js;
    }

    public async Task<bool> IsSupportedAsync()
        => await _js.InvokeAsync<bool>("folderPicker.isSupported");

    public async Task<string?> PickFolderAsync()
        => await _js.InvokeAsync<string?>("folderPicker.pick");

    public async Task<string?> GetStoredFolderNameAsync()
        => await _js.InvokeAsync<string?>("folderPicker.getStoredName");

    public async Task<bool> RequestPermissionAsync()
        => await _js.InvokeAsync<bool>("folderPicker.requestPermission");

    public async Task<List<LocalFileInfo>> EnumerateFilesAsync()
    {
        var results = await _js.InvokeAsync<List<LocalFileInfo>>("folderPicker.enumerate");
        return results ?? [];
    }

    public async Task StartProgressiveEnumerationAsync<T>(DotNetObjectReference<T> dotNetRef, int batchSize = 20) where T : class
        => await _js.InvokeVoidAsync("folderPicker.enumerateProgressive", dotNetRef, batchSize);

    public async Task<List<LocalFileInfo>?> LoadMetadataCacheAsync(string folderName)
        => await _js.InvokeAsync<List<LocalFileInfo>?>("folderPicker.loadMetadataCache", folderName);

    public async Task SaveMetadataCacheAsync(string folderName, IEnumerable<LocalFileInfo> files)
        => await _js.InvokeVoidAsync("folderPicker.saveMetadataCache", folderName, files);

    public async Task<HashSet<string>?> LoadExistingKeysCacheAsync(string folderName)
    {
        var result = await _js.InvokeAsync<List<string>?>("folderPicker.loadExistingKeysCache", folderName);
        return result == null ? null : [.. result];
    }

    public async Task SaveExistingKeysCacheAsync(string folderName, IEnumerable<string> keys)
        => await _js.InvokeVoidAsync("folderPicker.saveExistingKeysCache", folderName, keys);

    public async Task<DeviceCacheInfo?> GetDeviceCacheInfoAsync(string folderName)
        => await _js.InvokeAsync<DeviceCacheInfo?>("folderPicker.getDeviceCacheInfo", folderName);

    public async Task ClearDeviceCacheAsync(string folderName)
        => await _js.InvokeVoidAsync("folderPicker.clearDeviceCache", folderName);

    public async Task<Dictionary<string, string>> GetBlobUrlsBatchAsync(IEnumerable<string> relativePaths)
    {
        var result = await _js.InvokeAsync<Dictionary<string, string>?>(
            "folderPicker.getBlobUrlsBatch", relativePaths);
        return result ?? [];
    }

    public async Task<string?> GetBlobUrlAsync(string relativePath)
        => await _js.InvokeAsync<string?>("folderPicker.getBlobUrl", relativePath);

    public async Task RevokeBlobUrlAsync(string url)
        => await _js.InvokeVoidAsync("folderPicker.revokeBlobUrl", url);

    public async Task<byte[]?> ReadFileBytesAsync(string relativePath)
        => await _js.InvokeAsync<byte[]?>("folderPicker.readFileBytes", relativePath);

    public async Task<string?> ComputeChecksumAsync(string relativePath)
        => await _js.InvokeAsync<string?>("folderPicker.computeChecksum", relativePath);

    public async Task ClearAsync()
        => await _js.InvokeVoidAsync("folderPicker.clear");
}
