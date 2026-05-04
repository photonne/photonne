using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.DatabaseBackup;

public class BackupDocument
{
    // 1.0 = legacy schema, no ML tables, no IncludesMlData flag.
    // 2.0 = adds People/Faces/UserFaceAssignments/AssetEmbeddings/AssetDetectedObjects/
    //       AssetClassifiedScenes/AssetRecognizedTextLines/ExternalLibraryPermissions
    //       and the IncludesMlData flag (false = "essential only" backup).
    public string Version { get; set; } = "2.0";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    // False when the export skipped ML output tables to keep the file small.
    // The restore uses this to decide whether to wipe Asset.*CompletedAt timestamps
    // and AssetMlJob rows so the ML pipelines reprocess the restored assets.
    public bool IncludesMlData { get; set; } = true;

    public List<User> Users { get; set; } = [];
    public List<Folder> Folders { get; set; } = [];
    public List<FolderPermission> FolderPermissions { get; set; } = [];
    public List<Setting> Settings { get; set; } = [];
    public List<ExternalLibrary> ExternalLibraries { get; set; } = [];
    public List<ExternalLibraryPermission> ExternalLibraryPermissions { get; set; } = [];
    public List<UserTag> UserTags { get; set; } = [];
    public List<Album> Albums { get; set; } = [];
    public List<Asset> Assets { get; set; } = [];
    public List<AssetExif> AssetExifs { get; set; } = [];
    public List<AssetThumbnail> AssetThumbnails { get; set; } = [];
    public List<AssetTag> AssetTags { get; set; } = [];
    public List<AssetMlJob> AssetMlJobs { get; set; } = [];
    public List<AssetUserTag> AssetUserTags { get; set; } = [];
    public List<AlbumAsset> AlbumAssets { get; set; } = [];
    public List<AlbumPermission> AlbumPermissions { get; set; } = [];
    public List<SharedLink> SharedLinks { get; set; } = [];
    public List<RefreshToken> RefreshTokens { get; set; } = [];
    public List<Notification> Notifications { get; set; } = [];

    // ML output. Only populated when IncludesMlData = true.
    public List<Person> People { get; set; } = [];
    public List<Face> Faces { get; set; } = [];
    public List<UserFaceAssignment> UserFaceAssignments { get; set; } = [];
    public List<AssetEmbedding> AssetEmbeddings { get; set; } = [];
    public List<AssetDetectedObject> AssetDetectedObjects { get; set; } = [];
    public List<AssetClassifiedScene> AssetClassifiedScenes { get; set; } = [];
    public List<AssetRecognizedTextLine> AssetRecognizedTextLines { get; set; } = [];
}
