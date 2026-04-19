namespace Photonne.Client.Web.Services;

/// <summary>
/// Validates return-URL query parameters before redirecting the user after
/// login. Absolute URLs, scheme-relative URLs and protocol-relative payloads
/// are rejected to block open-redirect vectors.
/// </summary>
public static class ReturnUrlValidator
{
    public static bool IsSafe(string? returnUrl)
    {
        if (string.IsNullOrWhiteSpace(returnUrl))
        {
            return false;
        }

        // Reject anything that isn't a single-slash site-local path.
        // "//evil.com/x" parses as a scheme-relative URL in some browsers
        // and would redirect off-site.
        if (!returnUrl.StartsWith('/') || returnUrl.StartsWith("//", StringComparison.Ordinal))
        {
            return false;
        }

        return Uri.TryCreate(returnUrl, UriKind.Relative, out _);
    }
}
