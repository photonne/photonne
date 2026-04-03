using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Singleton que persiste el estado del Device entre navegaciones SPA.
/// Los blob URLs sobreviven a la navegación pero no a un page refresh.
/// </summary>
public class DeviceAssetsCache
{
    private readonly Dictionary<string, string> _blobUrls = new();

    public string? FolderName { get; private set; }
    public List<TimelineItem>? Assets { get; private set; }
    public HashSet<string> ServerExistingKeys { get; private set; } = [];
    public bool HasServerCheck { get; private set; }

    public bool IsValid(string folderName) =>
        Assets != null && FolderName == folderName;

    public void SetAssets(string folderName, List<TimelineItem> assets)
    {
        if (FolderName != folderName)
        {
            _blobUrls.Clear();
            HasServerCheck = false;
            ServerExistingKeys = [];
        }
        FolderName = folderName;
        Assets = assets;
    }

    public void SetServerExistingKeys(HashSet<string> keys)
    {
        ServerExistingKeys = keys;
        HasServerCheck = true;
    }

    public void Invalidate()
    {
        Assets = null;
        HasServerCheck = false;
        // Conservamos _blobUrls y FolderName: si el usuario vuelve a la misma carpeta,
        // los blob URLs previamente generados siguen siendo válidos (misma sesión).
    }

    public string? GetBlobUrl(string relativePath) =>
        _blobUrls.GetValueOrDefault(relativePath);

    public void MergeBlobUrls(Dictionary<string, string> urls)
    {
        foreach (var (k, v) in urls) _blobUrls[k] = v;
    }

    public bool HasBlobUrl(string relativePath) =>
        _blobUrls.ContainsKey(relativePath);
}
