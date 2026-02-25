using System.Security.Claims;
using Microsoft.AspNetCore.Components.Authorization;
using PhotoHub.Blazor.Shared.Services;

namespace PhotoHub.Blazor.MAUI.Services;

public class MauiAuthenticationStateProvider : AuthenticationStateProvider
{
    private readonly IAuthService _authService;

    public MauiAuthenticationStateProvider(IAuthService authService)
    {
        _authService = authService;
        if (_authService is MauiAuthService mauiAuth)
        {
            mauiAuth.OnAuthStateChanged += () => NotifyAuthenticationStateChanged(GetAuthenticationStateAsync());
        }
    }

    public override async Task<AuthenticationState> GetAuthenticationStateAsync()
    {
        var user = await _authService.GetCurrentUserAsync();
        if (user == null)
        {
            return new AuthenticationState(new ClaimsPrincipal(new ClaimsIdentity()));
        }

        var identity = new ClaimsIdentity(
            new[]
            {
                new Claim(ClaimTypes.Name, user.Username),
                new Claim(ClaimTypes.Email, user.Email ?? string.Empty),
                new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()),
                new Claim(ClaimTypes.Role, user.Role ?? string.Empty)
            },
            "PhotoHub");

        return new AuthenticationState(new ClaimsPrincipal(identity));
    }
}
