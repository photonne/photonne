namespace Photonne.Server.Api.Features.Folders;

public class FolderResponse
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
    public Guid? ExternalLibraryId { get; set; }
    public List<FolderResponse> SubFolders { get; set; } = new();
}
