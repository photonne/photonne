using System.Reflection;
using Microsoft.JSInterop;

namespace Photonne.Client.Web.Services;

public sealed class PwaUpdateService : IAsyncDisposable
{
    private IJSRuntime? _js;
    private DotNetObjectReference<PwaUpdateService>? _dotNetRef;
    private bool _initialized;

    public bool UpdateAvailable { get; private set; }
    public string CurrentVersion { get; private set; } = ReadAssemblyVersion();
    public string NewVersion { get; private set; } = string.Empty;
    public event Action? OnUpdateAvailable;

    public async Task InitAsync(IJSRuntime js)
    {
        if (_initialized) return;
        _initialized = true;
        _js = js;
        _dotNetRef = DotNetObjectReference.Create(this);
        await js.InvokeVoidAsync("pwaUpdate.setAppVersion", CurrentVersion);
        await js.InvokeVoidAsync("pwaUpdate.init", _dotNetRef);
    }

    private static string ReadAssemblyVersion()
    {
        var asm = typeof(PwaUpdateService).Assembly;
        var informational = asm.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;
        if (!string.IsNullOrWhiteSpace(informational))
        {
            var plus = informational.IndexOf('+');
            return plus >= 0 ? informational[..plus] : informational;
        }
        var v = asm.GetName().Version;
        return v is null ? string.Empty : $"{v.Major}.{v.Minor}.{v.Build}";
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

    public async Task<bool> CheckForUpdateAsync()
    {
        if (_js is null) return false;
        return await _js.InvokeAsync<bool>("pwaUpdate.checkForUpdate");
    }

    public ValueTask DisposeAsync()
    {
        _dotNetRef?.Dispose();
        return ValueTask.CompletedTask;
    }
}
