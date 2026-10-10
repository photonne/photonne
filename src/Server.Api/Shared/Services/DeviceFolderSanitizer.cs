using System.Text;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Sanitizes a client-supplied device name so it can safely be used as a
/// filesystem path segment under the user's MobileBackup directory.
///
/// Rules:
/// <list type="bullet">
/// <item>Allowed characters: letters, digits, dash, underscore, dot.</item>
/// <item>Whitespace inside the name collapses to underscore.</item>
/// <item>Anything else (slashes, emoji, control chars, path-traversal dots)
///   is dropped.</item>
/// <item>Leading/trailing <c>.</c> and <c>_</c> are stripped to keep hidden
///   files and dangling separators out.</item>
/// <item>Max 64 chars after sanitization (matches the username constraint
///   used elsewhere in the project).</item>
/// <item>Returns <c>null</c> when the input is missing or sanitizes to empty
///   — callers fall back to no subfolder.</item>
/// </list>
/// </summary>
public static class DeviceFolderSanitizer
{
    private const int MaxLength = 64;

    public static string? Sanitize(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return null;

        var sb = new StringBuilder(raw.Length);
        foreach (var c in raw.Trim())
        {
            if (char.IsLetterOrDigit(c) || c == '-' || c == '_' || c == '.')
            {
                sb.Append(c);
            }
            else if (char.IsWhiteSpace(c))
            {
                sb.Append('_');
            }
            // Drop everything else (slashes, ..\, emoji, control chars).
        }

        var s = sb.ToString().Trim('.', '_');
        if (s.Length == 0) return null;

        if (s.Length > MaxLength)
            s = s[..MaxLength].TrimEnd('.', '_');

        return s.Length > 0 ? s : null;
    }
}
