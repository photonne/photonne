using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Assets;

/// <summary>
/// Deletions from shared folders (/assets/shared/...) are gated by CanDelete and
/// land in a communal shared trash (/assets/shared/_trash) rather than a personal
/// one. This trash is administered via /api/assets/shared-trash: an Admin sees
/// every deletion; the deleter and a folder manager (CanManagePermissions) see
/// and can act on their own.
/// </summary>
public sealed class SharedFolderTrashTests : IntegrationTestBase
{
    public SharedFolderTrashTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record DeleteRequest(List<Guid> AssetIds);
    private sealed record RestoreRequest(List<Guid> AssetIds);
    private sealed record PurgeRequest(List<Guid> AssetIds);
    private sealed record SharedTrashItem(Guid Id, string? DeletedByUsername, string? DeletedFromFolderName);
    private sealed record SharedTrashPage(List<SharedTrashItem> Items, bool HasMore, DateTime? NextCursor);

    private async Task<Guid> CreateSharedFolderAsync(string path, string name, Guid? parentId = null)
    {
        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = name, ParentFolderId = parentId };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    private async Task<Guid> CreateSharedAssetAsync(Guid folderId, string fullPath, Guid ownerId, string fileName = "shared.jpg")
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = fullPath,
                FileSize = 2048,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                CapturedAt = DateTime.UtcNow,
                FolderId = folderId,
                OwnerId = ownerId
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    private Task GrantAsync(Guid folderId, Guid userId, Guid grantedBy,
        bool canRead = false, bool canWrite = false, bool canDelete = false, bool canManagePermissions = false)
        => WithDbContextAsync(async db =>
        {
            db.FolderPermissions.Add(new FolderPermission
            {
                FolderId = folderId,
                UserId = userId,
                GrantedByUserId = grantedBy,
                CanRead = canRead,
                CanWrite = canWrite,
                CanDelete = canDelete,
                CanManagePermissions = canManagePermissions
            });
            await db.SaveChangesAsync();
        });

    [Fact]
    public async Task Delete_MovesSharedAssetToSharedTrash_AndAttributesDeleter()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateSharedFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateSharedAssetAsync(folderId, "/assets/shared/project/shared.jpg", owner.Id);
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true, canDelete: true);

        var resp = await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetId }));
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        var asset = await WithDbContextAsync(db => db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));
        Assert.NotNull(asset.DeletedAt);
        // DeletedByUserId (set only for shared-space deletions) attributes the
        // deleter and identifies the item as shared trash.
        Assert.Equal(bob.Id, asset.DeletedByUserId);
        Assert.Equal(folderId, asset.DeletedFromFolderId);
        Assert.Equal("/assets/shared/project/shared.jpg", asset.DeletedFromPath);
    }

    [Fact]
    public async Task Delete_ReturnsForbidden_WhenUserLacksCanDelete()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateSharedFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateSharedAssetAsync(folderId, "/assets/shared/project/shared.jpg", owner.Id);
        // Read but NOT delete.
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true);

        var resp = await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetId }));
        Assert.Equal(HttpStatusCode.Forbidden, resp.StatusCode);

        var asset = await WithDbContextAsync(db => db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));
        Assert.Null(asset.DeletedAt);
    }

    [Fact]
    public async Task SharedTrashList_AdminSeesEveryone_UnrelatedUserSeesNothing()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var (_, strangerClient) = await CreateAuthenticatedUserAsync();
        var (_, adminClient) = await CreateAuthenticatedUserAsync(role: "Admin");

        var folderId = await CreateSharedFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateSharedAssetAsync(folderId, "/assets/shared/project/shared.jpg", owner.Id);
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true, canDelete: true);

        await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetId }));

        var adminPage = await adminClient.GetFromJsonAsync<SharedTrashPage>("/api/assets/shared-trash");
        Assert.NotNull(adminPage);
        Assert.Contains(adminPage!.Items, i => i.Id == assetId && i.DeletedByUsername == bob.Username);

        // A user with no relationship to the folder or deletion sees nothing.
        var strangerPage = await strangerClient.GetFromJsonAsync<SharedTrashPage>("/api/assets/shared-trash");
        Assert.NotNull(strangerPage);
        Assert.DoesNotContain(strangerPage!.Items, i => i.Id == assetId);

        // The deleter sees their own.
        var bobPage = await bobClient.GetFromJsonAsync<SharedTrashPage>("/api/assets/shared-trash");
        Assert.NotNull(bobPage);
        Assert.Contains(bobPage!.Items, i => i.Id == assetId);
    }

    [Fact]
    public async Task SharedTrashRestore_ReturnsAssetToOriginalFolder_AndClearsDeleter()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateSharedFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateSharedAssetAsync(folderId, "/assets/shared/project/shared.jpg", owner.Id);
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true, canDelete: true);

        await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetId }));

        var restore = await bobClient.PostAsJsonAsync("/api/assets/shared-trash/restore", new RestoreRequest(new() { assetId }));
        Assert.Equal(HttpStatusCode.NoContent, restore.StatusCode);

        var asset = await WithDbContextAsync(db => db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));
        Assert.Null(asset.DeletedAt);
        Assert.Null(asset.DeletedByUserId);
        Assert.Equal(folderId, asset.FolderId);
        Assert.Equal("/assets/shared/project/shared.jpg", asset.FullPath);
    }

    [Fact]
    public async Task SharedTrashRestore_AllowsFolderManager_ButNotUnrelatedUser()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var (manager, managerClient) = await CreateAuthenticatedUserAsync();
        var (_, strangerClient) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateSharedFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateSharedAssetAsync(folderId, "/assets/shared/project/shared.jpg", owner.Id);
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true, canDelete: true);
        await GrantAsync(folderId, manager.Id, owner.Id, canRead: true, canManagePermissions: true);

        await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetId }));

        // A stranger cannot restore.
        var strangerResp = await strangerClient.PostAsJsonAsync(
            "/api/assets/shared-trash/restore", new RestoreRequest(new() { assetId }));
        Assert.Equal(HttpStatusCode.Forbidden, strangerResp.StatusCode);

        // The folder manager can.
        var managerResp = await managerClient.PostAsJsonAsync(
            "/api/assets/shared-trash/restore", new RestoreRequest(new() { assetId }));
        Assert.Equal(HttpStatusCode.NoContent, managerResp.StatusCode);
    }

    [Fact]
    public async Task SharedTrashPurge_RemovesAssetPermanently()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var (_, adminClient) = await CreateAuthenticatedUserAsync(role: "Admin");

        var folderId = await CreateSharedFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateSharedAssetAsync(folderId, "/assets/shared/project/shared.jpg", owner.Id);
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true, canDelete: true);

        await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetId }));

        var purge = await adminClient.PostAsJsonAsync("/api/assets/shared-trash/purge", new PurgeRequest(new() { assetId }));
        Assert.Equal(HttpStatusCode.NoContent, purge.StatusCode);

        var exists = await WithDbContextAsync(db => db.Assets.AsNoTracking().AnyAsync(a => a.Id == assetId));
        Assert.False(exists);
    }

    [Fact]
    public async Task Delete_NotifiesAdminsAndManagers_ButNotTheDeleter()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var (manager, _) = await CreateAuthenticatedUserAsync();
        var (admin, _) = await CreateAuthenticatedUserAsync(role: "Admin");

        var folderId = await CreateSharedFolderAsync("/assets/shared/project", "project");
        var assetId = await CreateSharedAssetAsync(folderId, "/assets/shared/project/shared.jpg", owner.Id);
        await GrantAsync(folderId, bob.Id, owner.Id, canRead: true, canDelete: true);
        await GrantAsync(folderId, manager.Id, owner.Id, canRead: true, canManagePermissions: true);

        var resp = await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetId }));
        Assert.Equal(HttpStatusCode.NoContent, resp.StatusCode);

        var notifications = await WithDbContextAsync(db => db.Notifications
            .AsNoTracking()
            .Where(n => n.Type == NotificationType.SharedAssetsDeleted)
            .ToListAsync());

        // The folder manager and the admin are notified; the deleter (bob) is not.
        Assert.Contains(notifications, n => n.UserId == manager.Id);
        Assert.Contains(notifications, n => n.UserId == admin.Id);
        Assert.DoesNotContain(notifications, n => n.UserId == bob.Id);
        Assert.All(notifications, n => Assert.Equal("/shared-trash", n.ActionUrl));
    }

    [Fact]
    public async Task Delete_AcrossTwoSharedFolders_NotifiesPerFolder()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var (admin, _) = await CreateAuthenticatedUserAsync(role: "Admin");

        var folderA = await CreateSharedFolderAsync("/assets/shared/a", "a");
        var folderB = await CreateSharedFolderAsync("/assets/shared/b", "b");
        var assetA = await CreateSharedAssetAsync(folderA, "/assets/shared/a/one.jpg", owner.Id);
        var assetB = await CreateSharedAssetAsync(folderB, "/assets/shared/b/two.jpg", owner.Id);
        await GrantAsync(folderA, bob.Id, owner.Id, canRead: true, canDelete: true);
        await GrantAsync(folderB, bob.Id, owner.Id, canRead: true, canDelete: true);

        await bobClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest(new() { assetA, assetB }));

        // One aggregated notification per folder → the admin gets two.
        var adminNotifs = await WithDbContextAsync(db => db.Notifications
            .AsNoTracking()
            .CountAsync(n => n.UserId == admin.Id && n.Type == NotificationType.SharedAssetsDeleted));
        Assert.Equal(2, adminNotifs);
    }
}
