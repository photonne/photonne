using Microsoft.AspNetCore.Components;
using Microsoft.JSInterop;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Provides native-like back navigation: when invoked from a back button, it
/// triggers the browser's history.back() so the previous page restores its
/// scroll position, opened panels and other ephemeral state. Falls back to a
/// canonical URL only when there's no in-app history (e.g. deep link landing).
///
/// Pages should call <see cref="GoBackAsync(string)"/> from any "Volver"
/// button instead of <c>NavigationManager.NavigateTo</c> — the latter pushes
/// a brand new entry, losing the user's previous context.
/// </summary>
public sealed class BackNavigationService : IDisposable
{
    private readonly NavigationManager _navigation;
    private readonly IJSRuntime _js;
    private bool _initialized;

    public BackNavigationService(NavigationManager navigation, IJSRuntime js)
    {
        _navigation = navigation;
        _js = js;
        _navigation.LocationChanged += OnLocationChanged;
    }

    /// <summary>
    /// Called by MainLayout once the JS runtime is available. Stamps the
    /// initial history entry with depth 0 so subsequent navigations can be
    /// distinguished from cold starts.
    /// </summary>
    public async Task EnsureInitializedAsync()
    {
        if (_initialized) return;
        try
        {
            await _js.InvokeVoidAsync("appNavHelpers.init");
            // Stamp the current entry too, in case Blazor pushed before init ran.
            await _js.InvokeVoidAsync("appNavHelpers.onNavigate");
            _initialized = true;
        }
        catch (JSDisconnectedException) { }
        catch (TaskCanceledException) { }
    }

    /// <summary>
    /// Navigates back if there's previous in-app history; otherwise navigates
    /// to <paramref name="fallbackUrl"/>. Calling history.back() preserves the
    /// previous page's scroll position and ephemeral UI state.
    /// </summary>
    public async Task GoBackAsync(string fallbackUrl)
    {
        try
        {
            await EnsureInitializedAsync();
            var canGoBack = await _js.InvokeAsync<bool>("appNavHelpers.canGoBack");
            if (canGoBack)
            {
                await _js.InvokeVoidAsync("appNavHelpers.back");
                return;
            }
        }
        catch (JSDisconnectedException) { }
        catch (TaskCanceledException) { }

        _navigation.NavigateTo(fallbackUrl);
    }

    private void OnLocationChanged(object? sender, Microsoft.AspNetCore.Components.Routing.LocationChangedEventArgs e)
    {
        // Stamp depth on every navigation. Best-effort: if JS is unavailable
        // (very early during boot) we just skip; the next navigation catches up.
        _ = StampDepthAsync();
    }

    private async Task StampDepthAsync()
    {
        try
        {
            await _js.InvokeVoidAsync("appNavHelpers.onNavigate");
            _initialized = true;
        }
        catch (JSDisconnectedException) { }
        catch (TaskCanceledException) { }
        catch (InvalidOperationException) { /* JS not ready yet */ }
    }

    public void Dispose()
    {
        _navigation.LocationChanged -= OnLocationChanged;
    }
}
