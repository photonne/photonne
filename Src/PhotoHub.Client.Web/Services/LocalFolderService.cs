using Microsoft.JSInterop;

namespace PhotoHub.Client.Web.Services;

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

    public async Task<byte[]?> ReadFileBytesAsync(string relativePath)
        => await _js.InvokeAsync<byte[]?>("folderPicker.readFileBytes", relativePath);

    public async Task<string?> GetBlobUrlAsync(string relativePath)
        => await _js.InvokeAsync<string?>("folderPicker.getBlobUrl", relativePath);

    public async Task RevokeBlobUrlAsync(string url)
        => await _js.InvokeVoidAsync("folderPicker.revokeBlobUrl", url);

    public async Task<string?> ComputeChecksumAsync(string relativePath)
        => await _js.InvokeAsync<string?>("folderPicker.computeChecksum", relativePath);

    public async Task ClearAsync()
        => await _js.InvokeVoidAsync("folderPicker.clear");
}
