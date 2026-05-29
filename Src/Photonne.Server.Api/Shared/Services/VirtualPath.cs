namespace Photonne.Server.Api.Shared.Services;

public static class VirtualPath
{
    /// <summary>
    /// True when <paramref name="path"/> equals <paramref name="root"/> or sits
    /// inside it as a strict descendant. Both inputs are normalized (backslashes
    /// converted, trailing slashes ignored) and the comparison is
    /// case-insensitive. Guards against the prefix-collision bug where a naive
    /// <c>StartsWith("/assets/users/admin")</c> would also match
    /// <c>/assets/users/admin2/...</c>.
    /// </summary>
    public static bool IsUnder(string path, string root)
    {
        if (string.IsNullOrEmpty(path) || string.IsNullOrEmpty(root)) return false;
        var normalizedPath = path.Replace('\\', '/').TrimEnd('/');
        var normalizedRoot = root.Replace('\\', '/').TrimEnd('/');
        if (normalizedRoot.Length == 0) return false;
        return normalizedPath.Equals(normalizedRoot, System.StringComparison.OrdinalIgnoreCase)
            || normalizedPath.StartsWith(normalizedRoot + "/", System.StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>
    /// True when <paramref name="path"/> is a purely structural container that
    /// must never carry per-user permissions — currently <c>/assets</c> and the
    /// <c>/assets/users</c> root that holds everyone's personal spaces. A
    /// FolderPermission on either path would leak every user's content to the
    /// grantee, so the indexer must not auto-assign one and read-side queries
    /// must defensively exclude these folder ids even if a stale grant exists.
    /// </summary>
    public static bool IsStructuralContainer(string path)
    {
        if (string.IsNullOrEmpty(path)) return false;
        var normalized = path.Replace('\\', '/').TrimEnd('/');
        return normalized.Equals("/assets", System.StringComparison.OrdinalIgnoreCase)
            || normalized.Equals("/assets/users", System.StringComparison.OrdinalIgnoreCase);
    }
}
