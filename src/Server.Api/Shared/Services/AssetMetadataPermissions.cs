namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Single rule for who may edit an asset's own metadata (description, capture
/// date, user tags): the asset must live in the caller's personal space
/// (<c>/assets/users/{username}/</c>). The edit endpoints gate on it and the
/// detail endpoint reports it as <c>CanEdit</c>, so the client can hide what the
/// server would reject instead of the two drifting apart.
/// </summary>
public static class AssetMetadataPermissions
{
    public static bool IsInUserRoot(string assetPath, string username)
    {
        if (string.IsNullOrEmpty(username)) return false;
        var normalized = assetPath.Replace('\\', '/');
        return normalized.Contains($"/users/{username}/", StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>
    /// True when <paramref name="username"/> may edit the metadata of an asset
    /// at <paramref name="assetPath"/>. Trashed assets are not editable (the
    /// description and date endpoints answer 404 for them).
    /// </summary>
    public static bool CanEdit(string assetPath, bool isDeleted, string username)
        => !isDeleted && IsInUserRoot(assetPath, username);
}
