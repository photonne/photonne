using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Folders;

/// <summary>
/// Ownership/authorization of folders that carry no FolderPermission row — the
/// case for folders created directly on the server filesystem. Personal-space
/// folders are owned by their path's user; an admin owns the whole shared space
/// but never another user's personal space.
/// </summary>
public sealed class FolderOwnershipTests : IntegrationTestBase
{
    public FolderOwnershipTests(PhotonneApiFactory factory) : base(factory) { }

    // Wire shape of GET /api/folders entries (subset we assert on).
    private sealed record FolderDto(Guid Id, string Path, bool IsOwner, bool IsShared);

    private sealed record UpdateFolderBody(string Name, Guid? ParentFolderId);

    private async Task<Guid> CreateFolderAsync(string path) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private static async Task<FolderDto?> FindFolderAsync(HttpClient client, Guid id)
    {
        var folders = await client.GetFromJsonAsync<List<FolderDto>>("/api/folders");
        return folders?.FirstOrDefault(f => f.Id == id);
    }

    [Fact]
    public async Task PersonalFolder_WithoutPermissionRow_IsOwnedAndRenameable()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}/Camera");

        // The owner sees it as theirs even though no FolderPermission row exists.
        var listed = await FindFolderAsync(client, folderId);
        Assert.NotNull(listed);
        Assert.True(listed!.IsOwner);

        // And the rename actually goes through (path-based write authorization).
        var rename = await client.PutAsJsonAsync(
            $"/api/folders/{folderId}", new UpdateFolderBody("Camara", null));
        Assert.True(rename.IsSuccessStatusCode);
    }

    [Fact]
    public async Task Admin_OwnsAndCanRenameSharedFolder_WithoutPermissionRow()
    {
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        // A share created directly on disk: under /assets/shared, no permission row.
        var folderId = await CreateFolderAsync("/assets/shared/Familia");

        var listed = await FindFolderAsync(admin, folderId);
        Assert.NotNull(listed);
        Assert.True(listed!.IsShared);
        Assert.True(listed.IsOwner);

        var rename = await admin.PutAsJsonAsync(
            $"/api/folders/{folderId}", new UpdateFolderBody("Familia 2026", null));
        Assert.True(rename.IsSuccessStatusCode);
    }

    [Fact]
    public async Task NonAdmin_CannotSeeOrRenameSharedFolder_WithoutPermission()
    {
        var (_, user) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync("/assets/shared/Familia");

        // Not visible without an explicit permission row…
        Assert.Null(await FindFolderAsync(user, folderId));

        // …and writes are forbidden.
        var rename = await user.PutAsJsonAsync(
            $"/api/folders/{folderId}", new UpdateFolderBody("Hack", null));
        Assert.Equal(HttpStatusCode.Forbidden, rename.StatusCode);
    }

    [Fact]
    public async Task Admin_CannotRenameAnotherUsersPersonalFolder()
    {
        var bob = await CreateUserAsync();
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var folderId = await CreateFolderAsync($"/assets/users/{bob.Username}/Camera");

        // The admin's shared-space privilege must not reach private personal spaces.
        var rename = await admin.PutAsJsonAsync(
            $"/api/folders/{folderId}", new UpdateFolderBody("Snoop", null));
        Assert.Equal(HttpStatusCode.Forbidden, rename.StatusCode);
    }
}
