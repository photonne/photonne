using System.Security.Claims;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Convenience accessors for the JWT claims used throughout the API.
/// Keep these tiny and parsing-only — anything that needs the database belongs
/// in <see cref="UserStorageService"/>.
/// </summary>
public static class ClaimsPrincipalExtensions
{
    /// <summary>
    /// Returns the user GUID from the standard <see cref="ClaimTypes.NameIdentifier"/>
    /// claim, or <see cref="Guid.Empty"/> if not present / not parseable.
    /// </summary>
    public static Guid GetUserId(this ClaimsPrincipal user)
    {
        var raw = user.FindFirstValue(ClaimTypes.NameIdentifier);
        return Guid.TryParse(raw, out var id) ? id : Guid.Empty;
    }

    /// <summary>
    /// Returns the username from the custom <c>"username"</c> claim issued by
    /// <see cref="AuthService"/>. Empty string if missing.
    /// </summary>
    public static string GetUsername(this ClaimsPrincipal user)
        => user.FindFirstValue("username") ?? string.Empty;

    /// <summary>
    /// Returns the virtual root for the current user: <c>/assets/users/{username}</c>.
    /// Empty string when no username claim is present (treat as Unauthorized).
    /// </summary>
    public static string GetUserVirtualRoot(this ClaimsPrincipal user)
    {
        var username = user.GetUsername();
        return string.IsNullOrEmpty(username) ? string.Empty : UserStorageService.BuildVirtualRoot(username);
    }
}
