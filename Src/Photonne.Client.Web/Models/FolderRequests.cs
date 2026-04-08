namespace Photonne.Client.Web.Models;

public class CreateFolderRequest
{
    public string Name { get; set; } = string.Empty;
    public Guid? ParentFolderId { get; set; }
    public bool IsSharedSpace { get; set; }
}

public class UpdateFolderRequest
{
    public string Name { get; set; } = string.Empty;
    public Guid? ParentFolderId { get; set; }
}

public class MoveFolderAssetsRequest
{
    public Guid? SourceFolderId { get; set; }
    public Guid TargetFolderId { get; set; }
    public List<Guid> AssetIds { get; set; } = new();
}

public class RemoveFolderAssetsRequest
{
    public Guid FolderId { get; set; }
    public List<Guid> AssetIds { get; set; } = new();
}

public class DeleteAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class RestoreAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class PurgeAssetsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class DownloadZipRequest
{
    public List<Guid> AssetIds { get; set; } = new();
    public string? FileName { get; set; }
}
