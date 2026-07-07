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

    // Per-user opt-out: true when the requesting user has excluded this shared
    // folder from their own discovery surfaces (timeline, memories, people,
    // search…) while keeping it browsable/administrable here. See
    // AllowedFolderCache.ExcludedFoldersSettingKey.
    public bool ExcludedFromTimeline { get; set; }

    public List<FolderResponse> SubFolders { get; set; } = new();
}

public class TimelineVisibilityRequest
{
    // true  => include this folder in my timeline/memories/etc. (default)
    // false => I only administer it; keep it out of my personal surfaces.
    public bool Included { get; set; }
}
