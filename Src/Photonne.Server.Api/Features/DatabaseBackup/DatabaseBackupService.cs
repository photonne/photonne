using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;

namespace Photonne.Server.Api.Features.DatabaseBackup;

public class DatabaseBackupService
{
    private readonly ApplicationDbContext _dbContext;

    public DatabaseBackupService(ApplicationDbContext dbContext)
    {
        _dbContext = dbContext;
    }

    public async Task<BackupDocument> ExportAsync(CancellationToken ct)
    {
        return new BackupDocument
        {
            CreatedAt = DateTime.UtcNow,
            Users             = await _dbContext.Users.AsNoTracking().ToListAsync(ct),
            Folders           = await _dbContext.Folders.AsNoTracking().ToListAsync(ct),
            FolderPermissions = await _dbContext.FolderPermissions.AsNoTracking().ToListAsync(ct),
            Settings          = await _dbContext.Settings.AsNoTracking().ToListAsync(ct),
            ExternalLibraries = await _dbContext.ExternalLibraries.AsNoTracking().ToListAsync(ct),
            UserTags          = await _dbContext.UserTags.AsNoTracking().ToListAsync(ct),
            Albums            = await _dbContext.Albums.AsNoTracking().ToListAsync(ct),
            Assets            = await _dbContext.Assets.AsNoTracking().ToListAsync(ct),
            AssetExifs        = await _dbContext.AssetExifs.AsNoTracking().ToListAsync(ct),
            AssetThumbnails   = await _dbContext.AssetThumbnails.AsNoTracking().ToListAsync(ct),
            AssetTags         = await _dbContext.AssetTags.AsNoTracking().ToListAsync(ct),
            AssetMlJobs       = await _dbContext.AssetMlJobs.AsNoTracking().ToListAsync(ct),
            AssetUserTags     = await _dbContext.AssetUserTags.AsNoTracking().ToListAsync(ct),
            AlbumAssets       = await _dbContext.AlbumAssets.AsNoTracking().ToListAsync(ct),
            AlbumPermissions  = await _dbContext.AlbumPermissions.AsNoTracking().ToListAsync(ct),
            SharedLinks       = await _dbContext.SharedLinks.AsNoTracking().ToListAsync(ct),
            RefreshTokens     = await _dbContext.RefreshTokens.AsNoTracking().ToListAsync(ct),
            Notifications     = await _dbContext.Notifications.AsNoTracking().ToListAsync(ct),
        };
    }

    public async Task RestoreAsync(BackupDocument backup, CancellationToken ct)
    {
        await using var transaction = await _dbContext.Database.BeginTransactionAsync(ct);

        // Clear all tables using TRUNCATE CASCADE (PostgreSQL handles FK order automatically)
        await _dbContext.Database.ExecuteSqlRawAsync(@"
            TRUNCATE TABLE
                ""Notifications"", ""RefreshTokens"", ""SharedLinks"",
                ""AlbumPermissions"", ""AlbumAssets"", ""FolderPermissions"",
                ""AssetUserTags"", ""AssetMlJobs"", ""AssetThumbnails"", ""AssetTags"",
                ""AssetExifs"", ""Assets"", ""Albums"", ""UserTags"", ""ExternalLibraries"",
                ""Settings"", ""Folders"", ""Users""
            CASCADE", ct);

        // Insert in dependency order (parents before children)
        // Albums.CoverAssetId and Folders.ParentFolderId reference entities not yet inserted,
        // so we null them first and restore their values at the end.
        var albumCoverMap   = backup.Albums.Where(a => a.CoverAssetId.HasValue)
                                           .ToDictionary(a => a.Id, a => a.CoverAssetId!.Value);
        var folderParentMap = backup.Folders.Where(f => f.ParentFolderId.HasValue)
                                            .ToDictionary(f => f.Id, f => f.ParentFolderId!.Value);

        foreach (var album  in backup.Albums)  album.CoverAssetId   = null;
        foreach (var folder in backup.Folders) folder.ParentFolderId = null;

        // Clear navigation collections to avoid EF tracking conflicts
        ClearNavigationProperties(backup);

        await InsertBatchAsync(_dbContext.Users,             backup.Users,             ct);
        await InsertBatchAsync(_dbContext.ExternalLibraries, backup.ExternalLibraries, ct);
        await InsertBatchAsync(_dbContext.Folders,           backup.Folders,           ct);
        await InsertBatchAsync(_dbContext.Settings,          backup.Settings,          ct);
        await InsertBatchAsync(_dbContext.UserTags,          backup.UserTags,          ct);
        await InsertBatchAsync(_dbContext.Albums,            backup.Albums,            ct);
        await InsertBatchAsync(_dbContext.Assets,            backup.Assets,            ct);
        await InsertBatchAsync(_dbContext.AssetExifs,        backup.AssetExifs,        ct);
        await InsertBatchAsync(_dbContext.AssetThumbnails,   backup.AssetThumbnails,   ct);
        await InsertBatchAsync(_dbContext.AssetTags,         backup.AssetTags,         ct);
        await InsertBatchAsync(_dbContext.AssetMlJobs,       backup.AssetMlJobs,       ct);
        await InsertBatchAsync(_dbContext.AssetUserTags,     backup.AssetUserTags,     ct);
        await InsertBatchAsync(_dbContext.FolderPermissions, backup.FolderPermissions, ct);
        await InsertBatchAsync(_dbContext.AlbumAssets,       backup.AlbumAssets,       ct);
        await InsertBatchAsync(_dbContext.AlbumPermissions,  backup.AlbumPermissions,  ct);
        await InsertBatchAsync(_dbContext.SharedLinks,       backup.SharedLinks,       ct);
        await InsertBatchAsync(_dbContext.RefreshTokens,     backup.RefreshTokens,     ct);
        await InsertBatchAsync(_dbContext.Notifications,     backup.Notifications,     ct);

        // Restore deferred FK values
        foreach (var (albumId, coverId) in albumCoverMap)
        {
            await _dbContext.Albums
                .Where(a => a.Id == albumId)
                .ExecuteUpdateAsync(s => s.SetProperty(a => a.CoverAssetId, coverId), ct);
        }

        foreach (var (folderId, parentId) in folderParentMap)
        {
            await _dbContext.Folders
                .Where(f => f.Id == folderId)
                .ExecuteUpdateAsync(s => s.SetProperty(f => f.ParentFolderId, parentId), ct);
        }

        await transaction.CommitAsync(ct);
    }

    private async Task InsertBatchAsync<T>(Microsoft.EntityFrameworkCore.DbSet<T> dbSet, List<T> entities, CancellationToken ct) where T : class
    {
        if (entities.Count == 0) return;

        _dbContext.ChangeTracker.Clear();
        dbSet.AddRange(entities);
        await _dbContext.SaveChangesAsync(ct);
    }

    private static void ClearNavigationProperties(BackupDocument backup)
    {
        foreach (var u in backup.Users)
        {
            u.FolderPermissions.Clear();
            u.Assets.Clear();
            u.AlbumPermissions.Clear();
            u.OwnedAlbums.Clear();
            u.RefreshTokens.Clear();
            u.Tags.Clear();
            u.ExternalLibraries.Clear();
        }

        foreach (var f in backup.Folders)
        {
            f.ParentFolder = null;
            f.SubFolders.Clear();
            f.Permissions.Clear();
            f.Assets.Clear();
        }

        foreach (var a in backup.Assets)
        {
            a.Owner = null;
            a.Folder = null;
            a.ExternalLibrary = null;
            a.Exif = null;
            a.Thumbnails.Clear();
            a.Tags.Clear();
            a.UserTags.Clear();
            a.MlJobs.Clear();
        }

        foreach (var al in backup.Albums)
        {
            al.Owner = null!;
            al.CoverAsset = null;
            al.AlbumAssets.Clear();
            al.Permissions.Clear();
        }

        foreach (var el in backup.ExternalLibraries)
        {
            el.Owner = null!;
            el.Assets.Clear();
        }

        foreach (var ut in backup.UserTags)
        {
            ut.Owner = null!;
            ut.AssetLinks.Clear();
        }

        foreach (var ae in backup.AssetExifs)   ae.Asset = null!;
        foreach (var at in backup.AssetThumbnails) at.Asset = null!;
        foreach (var at in backup.AssetTags)    at.Asset = null!;
        foreach (var am in backup.AssetMlJobs)  am.Asset = null!;

        foreach (var aut in backup.AssetUserTags)
        {
            aut.Asset   = null!;
            aut.UserTag = null!;
        }

        foreach (var fp in backup.FolderPermissions)
        {
            fp.User          = null!;
            fp.Folder        = null!;
            fp.GrantedByUser = null;
        }

        foreach (var aa in backup.AlbumAssets)
        {
            aa.Album = null!;
            aa.Asset = null!;
        }

        foreach (var ap in backup.AlbumPermissions)
        {
            ap.Album         = null!;
            ap.User          = null!;
            ap.GrantedByUser = null;
        }

        foreach (var sl in backup.SharedLinks)
        {
            sl.Asset     = null;
            sl.Album     = null;
            sl.CreatedBy = null;
        }

        foreach (var rt in backup.RefreshTokens) rt.User = null!;
        foreach (var n  in backup.Notifications)  n.User  = null!;
    }
}
