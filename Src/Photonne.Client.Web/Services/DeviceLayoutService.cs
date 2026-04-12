using Microsoft.JSInterop;

namespace Photonne.Client.Web.Services;

public class DeviceLayoutService : IAsyncDisposable
{
    private readonly IJSRuntime _js;
    private DotNetObjectReference<DeviceLayoutService>? _objRef;
    private bool _initialized;

    /// <summary>
    /// True when viewport width is less than 960px (MudBlazor md breakpoint).
    /// </summary>
    public bool IsMobile { get; private set; }

    /// <summary>
    /// Fired when the device crosses the mobile/desktop breakpoint threshold.
    /// </summary>
    public event Action? OnChanged;

    public DeviceLayoutService(IJSRuntime js) => _js = js;

    public async Task InitializeAsync()
    {
        if (_initialized) return;
        _initialized = true;
        _objRef = DotNetObjectReference.Create(this);
        var width = await _js.InvokeAsync<int>("deviceLayout.initialize", _objRef);
        IsMobile = width < 960;
    }

    [JSInvokable]
    public void OnResize(int width)
    {
        var wasMobile = IsMobile;
        IsMobile = width < 960;
        if (wasMobile != IsMobile)
            OnChanged?.Invoke();
    }

    public async ValueTask DisposeAsync()
    {
        if (_objRef != null)
        {
            try { await _js.InvokeVoidAsync("deviceLayout.dispose"); }
            catch { /* component may already be disposed */ }
            _objRef.Dispose();
        }
    }
}
