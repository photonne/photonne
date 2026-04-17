using Microsoft.JSInterop;

namespace Photonne.Client.Web.Services;

public enum PwaInstallMode
{
    None,
    Native,
    IosManual
}

public sealed class PwaInstallService : IAsyncDisposable
{
    private IJSRuntime? _js;
    private DotNetObjectReference<PwaInstallService>? _dotNetRef;
    private bool _initialized;

    public bool IsInstallAvailable { get; private set; }
    public PwaInstallMode Mode { get; private set; } = PwaInstallMode.None;
    public event Action? OnInstallAvailabilityChanged;

    public async Task InitAsync(IJSRuntime js)
    {
        if (_initialized) return;
        _initialized = true;
        _js = js;
        _dotNetRef = DotNetObjectReference.Create(this);
        await js.InvokeVoidAsync("pwaInstall.init", _dotNetRef);
    }

    [JSInvokable]
    public void SetInstallAvailability(bool available, string mode)
    {
        var parsed = mode switch
        {
            "native" => PwaInstallMode.Native,
            "ios" => PwaInstallMode.IosManual,
            _ => PwaInstallMode.None
        };

        if (IsInstallAvailable == available && Mode == parsed) return;
        IsInstallAvailable = available;
        Mode = parsed;
        OnInstallAvailabilityChanged?.Invoke();
    }

    public async Task<bool> PromptInstallAsync()
    {
        if (_js is null) return false;
        return await _js.InvokeAsync<bool>("pwaInstall.promptInstall");
    }

    public async Task DismissAsync()
    {
        if (_js is null) return;
        IsInstallAvailable = false;
        Mode = PwaInstallMode.None;
        OnInstallAvailabilityChanged?.Invoke();
        await _js.InvokeVoidAsync("pwaInstall.dismiss");
    }

    public ValueTask DisposeAsync()
    {
        _dotNetRef?.Dispose();
        return ValueTask.CompletedTask;
    }
}
