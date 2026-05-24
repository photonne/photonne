using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.DatabaseBackup;

public class BackupDocument
{
    // 1.0 = legacy schema, no ML tables, no Includes* flags.
    // 2.0 = adds ML tables + IncludesMlData flag (false = "essential only").
    // 3.0 = splits the backup into three layers (config / library / ML) via
    //       IncludesConfig + IncludesLibrary + IncludesMlData. RefreshTokens
    //       dropped — session state, regenerated on next login.
    public string Version { get; set; } = "3.0";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    // Layer flags. Defaults are `true` so v2.0 backups (which omit these and
    // always contain Config + Library) deserialize with the correct semantics.
    // The restore wipes layers that aren't in the backup and resets the
    // matching Asset.*CompletedAt timestamps when ML is absent but library
    // is present.
    public bool IncludesConfig  { get; set; } = true;
    public bool IncludesLibrary { get; set; } = true;
    public bool IncludesMlData  { get; set; } = true;

    // --- Config layer ---------------------------------------------------
    public List<User> Users { get; set; } = [];
    public List<Folder> Folders { get; set; } = [];
    public List<FolderPermission> FolderPermissions { get; set; } = [];
    public List<Setting> Settings { get; set; } = [];
    public List<ExternalLibrary> ExternalLibraries { get; set; } = [];
    public List<ExternalLibraryPermission> ExternalLibraryPermissions { get; set; } = [];
    public List<UserTag> UserTags { get; set; } = [];

    // --- Library layer --------------------------------------------------
    public List<Album> Albums { get; set; } = [];
    public List<Asset> Assets { get; set; } = [];
    public List<AssetExif> AssetExifs { get; set; } = [];
    public List<AssetThumbnail> AssetThumbnails { get; set; } = [];
    public List<AssetTag> AssetTags { get; set; } = [];
    public List<AssetUserTag> AssetUserTags { get; set; } = [];
    public List<AlbumAsset> AlbumAssets { get; set; } = [];
    public List<AlbumPermission> AlbumPermissions { get; set; } = [];
    public List<SharedLink> SharedLinks { get; set; } = [];
    public List<Notification> Notifications { get; set; } = [];

    // --- ML layer (only populated when IncludesMlData = true) -----------
    public List<AssetMlJob> AssetMlJobs { get; set; } = [];
    public List<Person> People { get; set; } = [];
    public List<Face> Faces { get; set; } = [];
    public List<UserFaceAssignment> UserFaceAssignments { get; set; } = [];
    public List<AssetEmbedding> AssetEmbeddings { get; set; } = [];
    public List<AssetDetectedObject> AssetDetectedObjects { get; set; } = [];
    public List<AssetClassifiedScene> AssetClassifiedScenes { get; set; } = [];
    public List<AssetRecognizedTextLine> AssetRecognizedTextLines { get; set; } = [];
}
