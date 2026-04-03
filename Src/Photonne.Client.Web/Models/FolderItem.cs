using Photonne.Client.Web;

namespace Photonne.Client.Web.Models;

public class FolderItem
{
    public Guid Id { get; set; }
    public string Path { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public Guid? ParentFolderId { get; set; }
    public DateTime CreatedAt { get; set; }
    public int AssetCount { get; set; }
    public Guid? FirstAssetId { get; set; }
    public List<Guid> PreviewAssetIds { get; set; } = new();
    public bool IsShared { get; set; }
    public bool IsOwner { get; set; }
    public int SharedWithCount { get; set; }
    public List<FolderItem> SubFolders { get; set; } = new();

    public string? ThumbnailUrl => FirstAssetId.HasValue
        ? $"{ApiConfig.BaseUrl}/api/assets/{FirstAssetId.Value}/thumbnail?size=Medium"
        : null;

    public List<string> PreviewThumbnailUrls => PreviewAssetIds
        .Select(id => $"{ApiConfig.BaseUrl}/api/assets/{id}/thumbnail?size=Small")
        .ToList();
}
