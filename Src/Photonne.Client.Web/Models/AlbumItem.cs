namespace Photonne.Client.Web.Models;

public class AlbumItem
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public int AssetCount { get; set; }
    public string? CoverThumbnailUrl { get; set; }
    public List<string> PreviewThumbnailUrls { get; set; } = new();
    public bool IsOwner { get; set; }
    public bool IsShared { get; set; }
    public int SharedWithCount { get; set; }
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    public bool HasActiveShareLink { get; set; }
}
