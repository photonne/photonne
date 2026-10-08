namespace Photonne.Server.Api.Shared.Authorization;

/// <summary>
/// Keeps a web client's refresh token out of JavaScript. A client opts in with
/// <c>refreshTokenInCookie</c> on login; from then on the token travels only in
/// this HttpOnly cookie, scoped to <c>/api/auth</c>, and login/refresh answer
/// with an empty <c>refreshToken</c>. The access token stays in the page's
/// memory, so an XSS can at most use a session while the page is open, never
/// carry off one that lasts 30 days.
///
/// Cross-site use is blocked twice: SameSite=Strict, and refresh takes a JSON
/// body, which forces a CORS preflight that the server's credential-less CORS
/// policy fails.
/// </summary>
public static class RefreshTokenCookie
{
    public const string Name = "photonne_refresh";

    public static void Append(HttpContext context, string refreshToken, DateTime expiresAtUtc)
        => context.Response.Cookies.Append(Name, refreshToken, BuildOptions(context, expiresAtUtc));

    public static string? Read(HttpContext context)
        => context.Request.Cookies.TryGetValue(Name, out var value) && !string.IsNullOrEmpty(value) ? value : null;

    public static void Delete(HttpContext context)
        => context.Response.Cookies.Delete(Name, BuildOptions(context, expiresAtUtc: null));

    private static CookieOptions BuildOptions(HttpContext context, DateTime? expiresAtUtc) => new()
    {
        HttpOnly = true,
        Secure = context.Request.IsHttps,
        SameSite = SameSiteMode.Strict,
        Path = "/api/auth",
        Expires = expiresAtUtc,
        IsEssential = true,
    };
}
