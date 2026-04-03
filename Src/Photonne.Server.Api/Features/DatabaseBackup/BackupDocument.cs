using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.DatabaseBackup;

public class BackupDocument
{
    public string Version { get; set; } = "1.0";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public List<User> Users { get; set; } = [];
    public List<Folder> Folders { get; set; } = [];
    public List<FolderPermission> FolderPermissions { get; set; } = [];
    public List<Setting> Settings { get; set; } = [];
    public List<ExternalLibrary> ExternalLibraries { get; set; } = [];
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
}
