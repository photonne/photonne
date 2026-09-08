using Microsoft.JSInterop;

namespace Photonne.Client.Web.Services;

public class LayoutService
{
    private readonly IJSRuntime _jsRuntime;
    private const string DarkModeKey = "isDarkMode";

    public LayoutService(IJSRuntime jsRuntime)
    {
        _jsRuntime = jsRuntime;
    }

    public event Action? OnMajorUpdate;

    private bool _isDarkMode = true;
    public bool IsDarkMode
    {
        get => _isDarkMode;
        private set
        {
            if (_isDarkMode != value)
            {
                _isDarkMode = value;
                NotifyUpdate();
            }
        }
    }

    public async Task InitializeAsync()
    {
        try
        {
            var storedValue = await _jsRuntime.InvokeAsync<string>("localStorage.getItem", DarkModeKey);
            if (bool.TryParse(storedValue, out var isDark))
            {
                IsDarkMode = isDark;
            }
        }
        catch
        {
            // Fallback for environments where localStorage is not available
        }
    }

    public async Task ToggleDarkModeAsync()
    {
        IsDarkMode = !IsDarkMode;
        try
        {
            await _jsRuntime.InvokeVoidAsync("localStorage.setItem", DarkModeKey, IsDarkMode.ToString().ToLower());
        }
        catch
        {
            // Fallback
        }
    }

    private void NotifyUpdate() => OnMajorUpdate?.Invoke();
}
