using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Folders;

/// <summary>
/// Deleting a folder moves its whole subtree of assets to the trash (personal or
/// shared, per space) and then removes the folder records — rather than rejecting
/// non-empty folders. The managed library path is redirected to a per-run tmp
/// directory (see PhotonneApiFactory.InternalAssetsPath) so the trash-move's
/// directory creation doesn't need write access to /data/assets.
/// </summary>
public sealed class FolderDeleteTests : IntegrationTestBase
{
    public FolderDeleteTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record RestoreRequest(List<Guid> AssetIds);

    private Task<Guid> CreateFolderAsync(string path, string name, Guid? parentId = null)
        => WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = name, ParentFolderId = parentId };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private Task<Guid> CreateAssetAsync(string fullPath, Guid ownerId, Guid? folderId, string fileName = "photo.jpg")
        => WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = fullPath,
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                FolderId = folderId,
                OwnerId = ownerId
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

    private Task GrantAsync(Guid folderId, Guid userId, Guid grantedBy,
        bool canRead = false, bool canDelete = false)
        => WithDbContextAsync(async db =>
        {
            db.FolderPermissions.Add(new FolderPermission
            {
                FolderId = folderId,
                UserId = userId,
                GrantedByUserId = grantedBy,
                CanRead = canRead,
                CanDelete = canDelete
            });
            await db.SaveChangesAsync();
        });

    private Task SetGlobalSettingAsync(string key, string value)
        => WithDbContextAsync(async db =>
        {
            db.Settings.Add(new Setting { OwnerId = Guid.Empty, Key = key, Value = value });
            await db.SaveChangesAsync();
        });

    [Fact]
    public async Task Delete_PersonalFolderWithAssets_MovesSubtreeToTrash_AndRemovesFolders()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}/Vacaciones";

        var folderId = await CreateFolderAsync(root, "Vacaciones");
        var subId = await CreateFolderAsync($"{root}/2024", "2024", folderId);
        var topAsset = await CreateAssetAsync($"{root}/beach.jpg", alice.Id, folderId, "beach.jpg");
        var subAsset = await CreateAssetAsync($"{root}/2024/pool.jpg", alice.Id, subId, "pool.jpg");

        var resp = await aliceClient.DeleteAsync($"/api/folders/{folderId}");
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        var assets = await WithDbContextAsync(db => db.Assets.AsNoTracking()
            .Where(a => a.Id == topAsset || a.Id == subAsset).ToListAsync());
        Assert.All(assets, a =>
        {
            Assert.NotNull(a.DeletedAt);
            Assert.Null(a.DeletedByUserId);                 // personal trash, not shared
            Assert.NotEqual(folderId, a.FolderId);          // repointed to the trash folder
            Assert.NotEqual(subId, a.FolderId);
            // The original folders are deleted, so restore falls back to the path.
            Assert.Null(a.DeletedFromFolderId);
        });
        Assert.Contains(assets, a => a.Id == topAsset && a.DeletedFromPath == $"{root}/beach.jpg");
        Assert.Contains(assets, a => a.Id == subAsset && a.DeletedFromPath == $"{root}/2024/pool.jpg");

        // Both the folder and its subfolder records are gone.
        var foldersLeft = await WithDbContextAsync(db => db.Folders.AsNoTracking()
            .CountAsync(f => f.Id == folderId || f.Id == subId));
        Assert.Equal(0, foldersLeft);

        // The trashed assets can be restored (back to their original path, unassigned).
        var restore = await aliceClient.PostAsJsonAsync("/api/assets/restore",
            new RestoreRequest(new() { topAsset, subAsset }));
        Assert.Equal(HttpStatusCode.NoContent, restore.StatusCode);
        var restored = await WithDbContextAsync(db => db.Assets.AsNoTracking()
            .Where(a => a.Id == topAsset || a.Id == subAsset).ToListAsync());
        Assert.All(restored, a => Assert.Null(a.DeletedAt));
        Assert.Contains(restored, a => a.Id == topAsset && a.FullPath == $"{root}/beach.jpg");
    }

    [Fact]
    public async Task Delete_CatchesAssetsByPath_EvenWhenFolderIdIsNull()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}/Loose";

        var folderId = await CreateFolderAsync(root, "Loose");
        // Asset physically under the folder but not yet attached (FolderId == null).
        var assetId = await CreateAssetAsync($"{root}/orphan.jpg", alice.Id, folderId: null, "orphan.jpg");

        var resp = await aliceClient.DeleteAsync($"/api/folders/{folderId}");
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        var asset = await WithDbContextAsync(db => db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));
        Assert.NotNull(asset.DeletedAt);
    }

    [Fact]
    public async Task Delete_SharedFolderWithAssets_MovesToSharedTrash_WhenUserCanDelete()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateAssetAsync("/assets/shared/project/shared.jpg", owner.Id, folderId, "shared.jpg");
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true, canDelete: true);

        var resp = await bobClient.DeleteAsync($"/api/folders/{folderId}");
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        var asset = await WithDbContextAsync(db => db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));
        Assert.NotNull(asset.DeletedAt);
        Assert.Equal(bob.Id, asset.DeletedByUserId);        // attributed as a shared deletion
        // The source folder is deleted, so the restore-by-folder link is dropped.
        Assert.Null(asset.DeletedFromFolderId);
        Assert.Equal("/assets/shared/project/shared.jpg", asset.DeletedFromPath);
    }

    [Fact]
    public async Task Delete_SharedFolder_Forbidden_WhenUserLacksCanDelete()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateAssetAsync("/assets/shared/project/shared.jpg", owner.Id, folderId, "shared.jpg");
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true); // read only

        var resp = await bobClient.DeleteAsync($"/api/folders/{folderId}");
        Assert.Equal(HttpStatusCode.Forbidden, resp.StatusCode);

        var (assetUntouched, folderRemains) = await WithDbContextAsync(async db => (
            await db.Assets.AsNoTracking().AnyAsync(a => a.Id == assetId && a.DeletedAt == null),
            await db.Folders.AsNoTracking().AnyAsync(f => f.Id == folderId)));
        Assert.True(assetUntouched);
        Assert.True(folderRemains);
    }

    [Fact]
    public async Task Delete_TrashDisabled_PermanentlyDeletesContents()
    {
        await SetGlobalSettingAsync("TrashSettings.Enabled", "false");
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}/Purge";

        var folderId = await CreateFolderAsync(root, "Purge");
        var assetId = await CreateAssetAsync($"{root}/gone.jpg", alice.Id, folderId, "gone.jpg");

        var resp = await aliceClient.DeleteAsync($"/api/folders/{folderId}");
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        var (assetExists, folderExists) = await WithDbContextAsync(async db => (
            await db.Assets.AsNoTracking().AnyAsync(a => a.Id == assetId),
            await db.Folders.AsNoTracking().AnyAsync(f => f.Id == folderId)));
        Assert.False(assetExists);   // permanently removed, not trashed
        Assert.False(folderExists);
    }

    [Fact]
    public async Task Delete_EmptyFolder_Returns204_AndRemovesIt()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}/Empty", "Empty");

        var resp = await aliceClient.DeleteAsync($"/api/folders/{folderId}");
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        var exists = await WithDbContextAsync(db => db.Folders.AsNoTracking().AnyAsync(f => f.Id == folderId));
        Assert.False(exists);
    }
}
