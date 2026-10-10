using Microsoft.IdentityModel.JsonWebTokens;

namespace Photonne.Server.Api.Shared.Authorization;

/// <summary>
/// Carries the access token for browser media requests. An <c>&lt;img&gt;</c> or
/// <c>&lt;video&gt;</c> can't send an Authorization header, so the web clients
/// get the same JWT in an HttpOnly cookie that the bearer handler reads — but
/// only on GET/HEAD media routes (<see cref="IsMediaRequest"/>). Every other
/// endpoint still requires the header, so the cookie can't be used to forge a
/// mutation (no CSRF surface) and SameSite=Strict keeps it off cross-site loads.
///
/// The cookie lives exactly as long as the token inside it; login and refresh
/// rewrite it, logout deletes it. Native clients ignore it and keep using the
/// header.
/// </summary>
public static class MediaSessionCookie
{
    public const string Name = "photonne_media";

    private static readonly string[] MediaSuffixes = ["/thumbnail", "/content", "/motion"];

    public static void Append(HttpContext context, string accessToken)
    {
        DateTimeOffset expires;
        try
        {
            expires = new JsonWebToken(accessToken).ValidTo;
        }
        catch (ArgumentException)
        {
            return;
        }

        context.Response.Cookies.Append(Name, accessToken, BuildOptions(context, expires));
    }

    public static void Delete(HttpContext context)
        => context.Response.Cookies.Delete(Name, BuildOptions(context, expires: null));

    /// <summary>
    /// GET/HEAD on an asset, face or unsupported-file media route, or on one
    /// frame of a motion clip (<c>/motion/frames/{index}</c>). Public
    /// share media lives under <c>/api/share</c> and authenticates by its
    /// own token, so it's left out.
    /// </summary>
    public static bool IsMediaRequest(HttpRequest request)
    {
        if (!HttpMethods.IsGet(request.Method) && !HttpMethods.IsHead(request.Method)) return false;

        var path = request.Path;
        if (!path.StartsWithSegments("/api", StringComparison.OrdinalIgnoreCase)
            || path.StartsWithSegments("/api/share", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        var value = path.Value!;
        return MediaSuffixes.Any(suffix => value.EndsWith(suffix, StringComparison.OrdinalIgnoreCase))
            || IsMotionFrame(value);
    }

    private static bool IsMotionFrame(string path)
    {
        var slash = path.LastIndexOf('/');
        return slash > 0
            && path[..slash].EndsWith("/motion/frames", StringComparison.OrdinalIgnoreCase)
            && int.TryParse(path.AsSpan(slash + 1), out _);
    }

    private static CookieOptions BuildOptions(HttpContext context, DateTimeOffset? expires) => new()
    {
        HttpOnly = true,
        Secure = context.Request.IsHttps,
        SameSite = SameSiteMode.Strict,
        Path = "/api",
        Expires = expires,
        IsEssential = true,
    };
}
