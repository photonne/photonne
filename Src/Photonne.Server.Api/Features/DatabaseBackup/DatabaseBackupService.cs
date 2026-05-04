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

    public async Task<BackupDocument> ExportAsync(bool includeMlData, CancellationToken ct)
    {
        var doc = new BackupDocument
        {
            CreatedAt         = DateTime.UtcNow,
            IncludesMlData    = includeMlData,
            Users             = await _dbContext.Users.AsNoTracking().ToListAsync(ct),
            Folders           = await _dbContext.Folders.AsNoTracking().ToListAsync(ct),
            FolderPermissions = await _dbContext.FolderPermissions.AsNoTracking().ToListAsync(ct),
            Settings          = await _dbContext.Settings.AsNoTracking().ToListAsync(ct),
            ExternalLibraries          = await _dbContext.ExternalLibraries.AsNoTracking().ToListAsync(ct),
            ExternalLibraryPermissions = await _dbContext.ExternalLibraryPermissions.AsNoTracking().ToListAsync(ct),
            UserTags          = await _dbContext.UserTags.AsNoTracking().ToListAsync(ct),
            Albums            = await _dbContext.Albums.AsNoTracking().ToListAsync(ct),
            Assets            = await _dbContext.Assets.AsNoTracking().ToListAsync(ct),
            AssetExifs        = await _dbContext.AssetExifs.AsNoTracking().ToListAsync(ct),
            AssetThumbnails   = await _dbContext.AssetThumbnails.AsNoTracking().ToListAsync(ct),
            AssetTags         = await _dbContext.AssetTags.AsNoTracking().ToListAsync(ct),
            AssetUserTags     = await _dbContext.AssetUserTags.AsNoTracking().ToListAsync(ct),
            AlbumAssets       = await _dbContext.AlbumAssets.AsNoTracking().ToListAsync(ct),
            AlbumPermissions  = await _dbContext.AlbumPermissions.AsNoTracking().ToListAsync(ct),
            SharedLinks       = await _dbContext.SharedLinks.AsNoTracking().ToListAsync(ct),
            RefreshTokens     = await _dbContext.RefreshTokens.AsNoTracking().ToListAsync(ct),
            Notifications     = await _dbContext.Notifications.AsNoTracking().ToListAsync(ct),
        };

        if (includeMlData)
        {
            doc.AssetMlJobs              = await _dbContext.AssetMlJobs.AsNoTracking().ToListAsync(ct);
            doc.People                   = await _dbContext.People.AsNoTracking().ToListAsync(ct);
            doc.Faces                    = await _dbContext.Faces.AsNoTracking().ToListAsync(ct);
            doc.UserFaceAssignments      = await _dbContext.UserFaceAssignments.AsNoTracking().ToListAsync(ct);
            doc.AssetEmbeddings          = await _dbContext.AssetEmbeddings.AsNoTracking().ToListAsync(ct);
            doc.AssetDetectedObjects     = await _dbContext.AssetDetectedObjects.AsNoTracking().ToListAsync(ct);
            doc.AssetClassifiedScenes    = await _dbContext.AssetClassifiedScenes.AsNoTracking().ToListAsync(ct);
            doc.AssetRecognizedTextLines = await _dbContext.AssetRecognizedTextLines.AsNoTracking().ToListAsync(ct);
        }

        return doc;
    }

    public async Task RestoreAsync(BackupDocument backup, CancellationToken ct)
    {
        // A v1.0 backup predates the ML tables — treat it as essential-only no
        // matter what IncludesMlData says, so the timestamp reset kicks in and
        // the ML pipelines reprocess restored assets.
        var hasMlData = backup.IncludesMlData && backup.Version != "1.0";

        await using var transaction = await _dbContext.Database.BeginTransactionAsync(ct);

        // CASCADE handles ordering for us, but listing every table explicitly makes
        // it obvious at review time which ones the restore wipes.
        await _dbContext.Database.ExecuteSqlRawAsync(@"
            TRUNCATE TABLE
                ""Notifications"", ""RefreshTokens"", ""SharedLinks"",
                ""AlbumPermissions"", ""AlbumAssets"", ""FolderPermissions"",
                ""AssetUserTags"", ""AssetMlJobs"", ""AssetThumbnails"", ""AssetTags"",
                ""UserFaceAssignments"", ""Faces"", ""AssetEmbeddings"",
                ""AssetDetectedObjects"", ""AssetClassifiedScenes"", ""AssetRecognizedTextLines"",
                ""AssetExifs"", ""Assets"",
                ""People"", ""Albums"", ""UserTags"",
                ""ExternalLibraryPermissions"", ""ExternalLibraries"",
                ""Settings"", ""Folders"", ""Users""
            CASCADE", ct);

        // Defer self/forward-referencing FKs whose target rows aren't inserted yet.
        // Same pattern for Album.CoverAssetId, Folder.ParentFolderId, Person.CoverFaceId.
        var albumCoverMap   = backup.Albums.Where(a => a.CoverAssetId.HasValue)
                                           .ToDictionary(a => a.Id, a => a.CoverAssetId!.Value);
        var folderParentMap = backup.Folders.Where(f => f.ParentFolderId.HasValue)
                                            .ToDictionary(f => f.Id, f => f.ParentFolderId!.Value);
        var personCoverMap  = backup.People.Where(p => p.CoverFaceId.HasValue)
                                           .ToDictionary(p => p.Id, p => p.CoverFaceId!.Value);

        foreach (var album  in backup.Albums)  album.CoverAssetId    = null;
        foreach (var folder in backup.Folders) folder.ParentFolderId = null;
        foreach (var person in backup.People)  person.CoverFaceId    = null;

        // When the backup didn't ship ML output, the Asset.*CompletedAt timestamps
        // would mislead the ML pipeline into thinking work was done. Wipe them so
        // the workers reprocess from scratch.
        if (!hasMlData)
        {
            foreach (var asset in backup.Assets)
            {
                asset.FaceRecognitionCompletedAt     = null;
                asset.ObjectDetectionCompletedAt     = null;
                asset.SceneClassificationCompletedAt = null;
                asset.TextRecognitionCompletedAt     = null;
                asset.ImageEmbeddingCompletedAt      = null;
            }
            // Defensive: a v1.0 file may carry MlJobs; drop them since we just
            // erased the timestamps that would've justified their Completed status.
            backup.AssetMlJobs.Clear();
        }

        ClearNavigationProperties(backup);

        // Insert in dependency order: parents → children.
        await InsertBatchAsync(_dbContext.Users,             backup.Users,             ct);
        await InsertBatchAsync(_dbContext.ExternalLibraries, backup.ExternalLibraries, ct);
        await InsertBatchAsync(_dbContext.ExternalLibraryPermissions, backup.ExternalLibraryPermissions, ct);
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

        if (hasMlData)
        {
            // People must exist before Faces (Face.PersonId / SuggestedPersonId).
            await InsertBatchAsync(_dbContext.People,                   backup.People,                   ct);
            await InsertBatchAsync(_dbContext.Faces,                    backup.Faces,                    ct);
            await InsertBatchAsync(_dbContext.UserFaceAssignments,      backup.UserFaceAssignments,      ct);
            await InsertBatchAsync(_dbContext.AssetEmbeddings,          backup.AssetEmbeddings,          ct);
            await InsertBatchAsync(_dbContext.AssetDetectedObjects,     backup.AssetDetectedObjects,     ct);
            await InsertBatchAsync(_dbContext.AssetClassifiedScenes,    backup.AssetClassifiedScenes,    ct);
            await InsertBatchAsync(_dbContext.AssetRecognizedTextLines, backup.AssetRecognizedTextLines, ct);
        }

        // Restore deferred FK values now that every target row exists.
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

        if (hasMlData)
        {
            foreach (var (personId, coverFaceId) in personCoverMap)
            {
                await _dbContext.People
                    .Where(p => p.Id == personId)
                    .ExecuteUpdateAsync(s => s.SetProperty(p => p.CoverFaceId, coverFaceId), ct);
            }
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
            u.ExternalLibraryPermissions.Clear();
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
            a.Embedding = null;
            a.Thumbnails.Clear();
            a.Tags.Clear();
            a.UserTags.Clear();
            a.MlJobs.Clear();
            a.Faces.Clear();
            a.DetectedObjects.Clear();
            a.ClassifiedScenes.Clear();
            a.RecognizedTextLines.Clear();
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
            el.Permissions.Clear();
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

        foreach (var elp in backup.ExternalLibraryPermissions)
        {
            elp.ExternalLibrary = null!;
            elp.User            = null!;
            elp.GrantedByUser   = null;
        }

        foreach (var p in backup.People)
        {
            p.Owner     = null!;
            p.CoverFace = null;
            p.Faces.Clear();
        }

        foreach (var f in backup.Faces)
        {
            f.Asset           = null!;
            f.Person          = null;
            f.SuggestedPerson = null;
        }

        foreach (var ufa in backup.UserFaceAssignments)
        {
            ufa.Face            = null!;
            ufa.User            = null!;
            ufa.Person          = null;
            ufa.SuggestedPerson = null;
        }

        foreach (var ae in backup.AssetEmbeddings)          ae.Asset = null!;
        foreach (var ado in backup.AssetDetectedObjects)    ado.Asset = null!;
        foreach (var acs in backup.AssetClassifiedScenes)   acs.Asset = null!;
        foreach (var artl in backup.AssetRecognizedTextLines) artl.Asset = null!;
    }
}
