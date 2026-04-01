using Microsoft.JSInterop;

namespace PhotoHub.Client.Web.Services;

public sealed class PwaUpdateService : IAsyncDisposable
{
    private IJSRuntime? _js;
    private DotNetObjectReference<PwaUpdateService>? _dotNetRef;
    private bool _initialized;

    public bool UpdateAvailable { get; private set; }
    public string CurrentVersion { get; private set; } = string.Empty;
    public string NewVersion { get; private set; } = string.Empty;
    public event Action? OnUpdateAvailable;

    public async Task InitAsync(IJSRuntime js)
    {
        if (_initialized) return;
        _initialized = true;
        _js = js;
        _dotNetRef = DotNetObjectReference.Create(this);
        await js.InvokeVoidAsync("pwaUpdate.init", _dotNetRef);
    }

    [JSInvokable]
    public void SetUpdateAvailability(bool isAvailable, string currentVersion, string newVersion)
    {
        if (UpdateAvailable == isAvailable)
            return;

        UpdateAvailable = isAvailable;
        CurrentVersion = currentVersion;
        NewVersion = newVersion;
        OnUpdateAvailable?.Invoke();
    }

    public async Task ApplyUpdateAsync()
    {
        if (_js is null) return;
        await _js.InvokeVoidAsync("pwaUpdate.applyUpdate");
    }

    public async Task CheckForUpdateAsync()
    {
        if (_js is null) return;
        await _js.InvokeVoidAsync("pwaUpdate.checkForUpdate");
    }

    public ValueTask DisposeAsync()
    {
        _dotNetRef?.Dispose();
        return ValueTask.CompletedTask;
    }
}
