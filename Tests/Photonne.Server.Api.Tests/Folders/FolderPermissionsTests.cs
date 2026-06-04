using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Folders;

/// <summary>
/// Photonne's folder ACL has two paths that a user can have access by:
///   1. Implicit: folder path starts with /assets/users/{username} (personal
///      space) — unconditional, even if spurious permission rows exist.
///   2. Explicit: a FolderPermission row with CanRead=true, inherited by the
///      folder's whole subtree.
/// There is NO admin bypass for app content: the Admin role only gates admin
/// endpoints. These tests lock down all branches.
/// </summary>
public sealed class FolderPermissionsTests : IntegrationTestBase
{
    public FolderPermissionsTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<Guid> CreateFolderAsync(string path, string name, Action<Folder>? customize = null)
    {
        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder
            {
                Path = path,
                Name = name
            };
            customize?.Invoke(folder);
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    [Fact]
    public async Task Owner_CanAccess_TheirPersonalFolder()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateFolderAsync(
            path: $"/assets/users/{alice.Username}/photos",
            name: "photos");

        var response = await aliceClient.GetAsync($"/api/folders/{folderId}");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task OtherUser_CannotAccess_SomeoneElsesPersonalFolder()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceFolderId = await CreateFolderAsync(
            path: $"/assets/users/{alice.Username}/private",
            name: "private");

        var response = await bobClient.GetAsync($"/api/folders/{aliceFolderId}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task User_WithExplicitReadPermission_CanAccess_SharedFolder()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var sharedFolderId = await CreateFolderAsync(
            path: "/assets/shared/project",
            name: "project");

        await GrantFolderPermissionAsync(sharedFolderId, alice.Id, grantedByUserId: alice.Id, canRead: true);
        // Bob has no permission row.

        var aliceResponse = await aliceClient.GetAsync($"/api/folders/{sharedFolderId}");
        Assert.Equal(HttpStatusCode.OK, aliceResponse.StatusCode);

        var bobResponse = await bobClient.GetAsync($"/api/folders/{sharedFolderId}");
        Assert.Equal(HttpStatusCode.Forbidden, bobResponse.StatusCode);
    }

    [Fact]
    public async Task User_WithReadPermission_OnSharedFolder_CanAccess_ItsSubfolders()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var parentId = await CreateFolderAsync(
            path: "/assets/shared/project",
            name: "project");
        var subfolderId = await CreateFolderAsync(
            path: "/assets/shared/project/designs",
            name: "designs",
            customize: f => f.ParentFolderId = parentId);

        // Permission granted only on the parent — it must propagate to the subtree.
        await GrantFolderPermissionAsync(parentId, alice.Id, grantedByUserId: alice.Id, canRead: true);

        var aliceResponse = await aliceClient.GetAsync($"/api/folders/{subfolderId}");
        Assert.Equal(HttpStatusCode.OK, aliceResponse.StatusCode);

        // Bob, without any grant, still cannot reach the subfolder.
        var bobResponse = await bobClient.GetAsync($"/api/folders/{subfolderId}");
        Assert.Equal(HttpStatusCode.Forbidden, bobResponse.StatusCode);

        // The inherited subfolder also surfaces in the user's folder list.
        var folders = await aliceClient.GetFromJsonAsync<List<FolderListItem>>("/api/folders");
        Assert.NotNull(folders);
        Assert.Contains(folders!, f => f.Id == subfolderId);
    }

    [Fact]
    public async Task Admin_HasNoBypass_ForOtherUsersFolders()
    {
        // Admin user is seeded by the factory.
        var admin = new TestUser(
            Guid.Empty, // not used; login uses username/password
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            "Admin");
        var adminClient = await LoginAsClientAsync(admin);

        var (alice, _) = await CreateAuthenticatedUserAsync();
        var aliceFolderId = await CreateFolderAsync(
            path: $"/assets/users/{alice.Username}/secrets",
            name: "secrets");

        // App-level visibility is permission-driven for EVERYONE: the Admin
        // role only gates admin endpoints (indexing, metadata, users). For
        // browsing content, an admin behaves like any other user.
        var response = await adminClient.GetAsync($"/api/folders/{aliceFolderId}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task Owner_SeesPersonalFolder_EvenWith_SpuriousPermissionRow()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();

        var folderId = await CreateFolderAsync(
            path: $"/assets/users/{alice.Username}/photos",
            name: "photos");

        // A stray permission row (e.g. legacy auto-grant noise) on a personal
        // folder must NOT hide it from its owner: personal space is
        // unconditional, permission rows only ADD access for others.
        await GrantFolderPermissionAsync(folderId, bob.Id, grantedByUserId: bob.Id, canRead: true);

        var detail = await aliceClient.GetAsync($"/api/folders/{folderId}");
        Assert.Equal(HttpStatusCode.OK, detail.StatusCode);

        var folders = await aliceClient.GetFromJsonAsync<List<FolderListItem>>("/api/folders");
        Assert.NotNull(folders);
        Assert.Contains(folders!, f => f.Id == folderId);
    }

    [Fact]
    public async Task RevokingParentGrant_HidesSubtree()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var parentId = await CreateFolderAsync(
            path: "/assets/shared/project",
            name: "project");
        var subfolderId = await CreateFolderAsync(
            path: "/assets/shared/project/designs",
            name: "designs",
            customize: f => f.ParentFolderId = parentId);

        // Alice manages the share; Bob reads via the inherited parent grant.
        await GrantFolderPermissionAsync(parentId, alice.Id, grantedByUserId: alice.Id,
            canRead: true, canWrite: true, canDelete: true, canManagePermissions: true);
        await GrantFolderPermissionAsync(parentId, bob.Id, grantedByUserId: alice.Id, canRead: true);

        var visible = await bobClient.GetAsync($"/api/folders/{subfolderId}");
        Assert.Equal(HttpStatusCode.OK, visible.StatusCode);

        // Revoke through the endpoint (it also invalidates Bob's cached
        // folder-visibility set) — the whole subtree must disappear for Bob.
        var revoke = await aliceClient.DeleteAsync($"/api/folders/{parentId}/permissions/{bob.Id}");
        Assert.Equal(HttpStatusCode.NoContent, revoke.StatusCode);

        var hidden = await bobClient.GetAsync($"/api/folders/{subfolderId}");
        Assert.Equal(HttpStatusCode.Forbidden, hidden.StatusCode);
    }

    [Fact]
    public async Task Timeline_ShowsAssetsInSubfolder_WhenOnlyParentIsGranted()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var parentId = await CreateFolderAsync(
            path: "/assets/shared/family",
            name: "family");
        var subfolderId = await CreateFolderAsync(
            path: "/assets/shared/family/2010",
            name: "2010",
            customize: f => f.ParentFolderId = parentId);

        var assetId = await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = "old-photo.jpg",
                FullPath = "/assets/shared/family/2010/old-photo.jpg",
                FileSize = 1234,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = DateTime.UtcNow.AddYears(-16),
                FileModifiedAt = DateTime.UtcNow.AddYears(-16),
                CapturedAt = DateTime.UtcNow.AddYears(-16),
                FolderId = subfolderId,
                OwnerId = alice.Id
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

        // Grant CanRead on the PARENT only — the timeline must surface the
        // asset that lives in the subfolder (read-time inheritance).
        await GrantFolderPermissionAsync(parentId, bob.Id, grantedByUserId: alice.Id, canRead: true);

        var response = await bobClient.GetAsync("/api/assets/timeline?pageSize=150");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync();
        Assert.Contains(assetId.ToString(), body);
    }

    [Fact]
    public async Task ListAll_OnlyReturnsFolders_TheUserCanSee()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();

        var aliceFolderId = await CreateFolderAsync(
            path: $"/assets/users/{alice.Username}/travel",
            name: "travel");
        var bobFolderId = await CreateFolderAsync(
            path: $"/assets/users/{bob.Username}/travel",
            name: "travel");

        var folders = await aliceClient.GetFromJsonAsync<List<FolderListItem>>("/api/folders");

        Assert.NotNull(folders);
        Assert.Contains(folders!, f => f.Id == aliceFolderId);
        Assert.DoesNotContain(folders!, f => f.Id == bobFolderId);
    }

    private sealed record FolderListItem(Guid Id, string Path, string Name);

    private Task GrantFolderPermissionAsync(
        Guid folderId,
        Guid userId,
        Guid grantedByUserId,
        bool canRead = false,
        bool canWrite = false,
        bool canDelete = false,
        bool canManagePermissions = false)
    {
        return WithDbContextAsync(async db =>
        {
            db.FolderPermissions.Add(new FolderPermission
            {
                FolderId = folderId,
                UserId = userId,
                GrantedByUserId = grantedByUserId,
                CanRead = canRead,
                CanWrite = canWrite,
                CanDelete = canDelete,
                CanManagePermissions = canManagePermissions
            });
            await db.SaveChangesAsync();
        });
    }
}
