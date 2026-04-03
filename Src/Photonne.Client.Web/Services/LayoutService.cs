using Microsoft.AspNetCore.Components;
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

    private bool _isNavbarCustom;
    public bool IsNavbarCustom
    {
        get => _isNavbarCustom;
        private set
        {
            if (_isNavbarCustom != value)
            {
                _isNavbarCustom = value;
                NotifyUpdate();
            }
        }
    }

    private RenderFragment? _navbarContent;
    public RenderFragment? NavbarContent
    {
        get => _navbarContent;
        private set
        {
            _navbarContent = value;
            NotifyUpdate();
        }
    }

    private bool _keepDrawerVisible;
    public bool KeepDrawerVisible
    {
        get => _keepDrawerVisible;
        private set
        {
            if (_keepDrawerVisible != value)
            {
                _keepDrawerVisible = value;
                NotifyUpdate();
            }
        }
    }

    private bool _isOverlayNavbar;
    public bool IsOverlayNavbar
    {
        get => _isOverlayNavbar;
        private set
        {
            if (_isOverlayNavbar != value)
            {
                _isOverlayNavbar = value;
                NotifyUpdate();
            }
        }
    }

    // True when in full-screen mode (e.g. AssetDetail) — hides bottom nav and FAB
    public bool HideBottomNav => _isOverlayNavbar;

    public void SetCustomNavbar(RenderFragment? content, bool keepDrawerVisible = false, bool overlay = false)
    {
        IsNavbarCustom = true;
        IsOverlayNavbar = overlay;
        NavbarContent = content;
        KeepDrawerVisible = keepDrawerVisible;
    }

    public void ResetNavbar()
    {
        var changed = _isNavbarCustom || _navbarContent != null || _keepDrawerVisible || _isOverlayNavbar;
        _isNavbarCustom = false;
        _navbarContent = null;
        _keepDrawerVisible = false;
        _isOverlayNavbar = false;
        if (changed)
        {
            NotifyUpdate();
        }
    }

    private void NotifyUpdate() => OnMajorUpdate?.Invoke();
}
