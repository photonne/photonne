using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Folders;

/// <summary>
/// Folder pins mirror album pins: PUT/DELETE /api/folders/{id}/pin only touch
/// the caller's own row, are idempotent, show up in the list and the detail,
/// and 404 for folders the caller can't read.
/// </summary>
public sealed class FolderPinTests : IntegrationTestBase
{
    public FolderPinTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record FolderDto(Guid Id, string Name, bool IsPinned, DateTime? PinnedAt);
    private sealed record PinDto(Guid FolderId, bool IsPinned, DateTime? PinnedAt);

    private Task<Guid> CreateFolderAsync(string path) =>
        WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private Task GrantReadAsync(Guid folderId, Guid userId, Guid grantedBy) =>
        WithDbContextAsync(async db =>
        {
            db.FolderPermissions.Add(new FolderPermission
            {
                FolderId = folderId, UserId = userId, GrantedByUserId = grantedBy, CanRead = true
            });
            await db.SaveChangesAsync();
        });

    private static async Task<FolderDto> FindInListAsync(HttpClient client, Guid folderId)
    {
        var list = await client.GetFromJsonAsync<List<FolderDto>>("/api/folders");
        Assert.NotNull(list);
        return Assert.Single(list!, f => f.Id == folderId);
    }

    [Fact]
    public async Task Pin_And_Unpin_AreIdempotent_AndShowInListAndDetail()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        // Under the personal root: the root itself is a structural container.
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}/Viajes");

        Assert.False((await FindInListAsync(aliceClient, folderId)).IsPinned);

        var first = await aliceClient.PutAsync($"/api/folders/{folderId}/pin", null);
        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        var firstPin = await first.Content.ReadFromJsonAsync<PinDto>();
        Assert.True(firstPin!.IsPinned);
        Assert.NotNull(firstPin.PinnedAt);

        // Second PUT keeps the original pin instead of failing or re-stamping it.
        var second = await aliceClient.PutAsync($"/api/folders/{folderId}/pin", null);
        Assert.Equal(HttpStatusCode.OK, second.StatusCode);
        var secondPin = await second.Content.ReadFromJsonAsync<PinDto>();
        Assert.Equal(firstPin.PinnedAt, secondPin!.PinnedAt);

        var rows = await WithDbContextAsync(db =>
            db.FolderPins.CountAsync(p => p.FolderId == folderId && p.UserId == alice.Id));
        Assert.Equal(1, rows);

        // The list is cached per user; pinning must invalidate it.
        var listed = await FindInListAsync(aliceClient, folderId);
        Assert.True(listed.IsPinned);
        Assert.NotNull(listed.PinnedAt);
        var detail = await aliceClient.GetFromJsonAsync<FolderDto>($"/api/folders/{folderId}");
        Assert.True(detail!.IsPinned);

        var unpin = await aliceClient.DeleteAsync($"/api/folders/{folderId}/pin");
        Assert.Equal(HttpStatusCode.OK, unpin.StatusCode);
        var unpinAgain = await aliceClient.DeleteAsync($"/api/folders/{folderId}/pin");
        Assert.Equal(HttpStatusCode.OK, unpinAgain.StatusCode);

        var afterUnpin = await FindInListAsync(aliceClient, folderId);
        Assert.False(afterUnpin.IsPinned);
        Assert.Null(afterUnpin.PinnedAt);
    }

    [Fact]
    public async Task MemberPinningSharedFolder_DoesNotPinItForOthers()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/shared/familia-{Guid.NewGuid():N}");
        await GrantReadAsync(folderId, alice.Id, alice.Id);
        await GrantReadAsync(folderId, bob.Id, alice.Id);

        (await bobClient.PutAsync($"/api/folders/{folderId}/pin", null)).EnsureSuccessStatusCode();

        Assert.True((await FindInListAsync(bobClient, folderId)).IsPinned);
        Assert.False((await FindInListAsync(aliceClient, folderId)).IsPinned);
    }

    [Fact]
    public async Task Pin_ReturnsNotFound_ForFolderTheUserCannotRead()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}/Privada");

        Assert.Equal(HttpStatusCode.NotFound,
            (await bobClient.PutAsync($"/api/folders/{folderId}/pin", null)).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,
            (await bobClient.DeleteAsync($"/api/folders/{folderId}/pin")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,
            (await bobClient.PutAsync($"/api/folders/{Guid.NewGuid()}/pin", null)).StatusCode);
    }

    [Fact]
    public async Task DeletingFolder_CascadesItsPins()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}/Temporal");
        (await aliceClient.PutAsync($"/api/folders/{folderId}/pin", null)).EnsureSuccessStatusCode();

        await WithDbContextAsync(async db =>
        {
            await db.Folders.Where(f => f.Id == folderId).ExecuteDeleteAsync();
        });

        var remaining = await WithDbContextAsync(db => db.FolderPins.CountAsync(p => p.FolderId == folderId));
        Assert.Equal(0, remaining);
    }
}
