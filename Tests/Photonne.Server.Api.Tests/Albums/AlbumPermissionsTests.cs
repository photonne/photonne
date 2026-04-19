using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Albums;

/// <summary>
/// Verifies that album visibility and mutation respect OwnerId + AlbumPermission
/// flags: a user who doesn't own an album and has no explicit permission must
/// not see it, read it, or modify it.
/// </summary>
public sealed class AlbumPermissionsTests : IntegrationTestBase
{
    public AlbumPermissionsTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record AlbumDto(Guid Id, string Name, bool IsOwner, bool CanRead, bool CanWrite, bool CanDelete);
    private sealed record CreateAlbum(string Name, string? Description = null);
    private sealed record UpdateAlbum(string Name, string? Description = null);

    private async Task<Guid> CreateAlbumForAsync(HttpClient client, string name)
    {
        var response = await client.PostAsJsonAsync("/api/albums", new CreateAlbum(name));
        response.EnsureSuccessStatusCode();
        var album = await response.Content.ReadFromJsonAsync<AlbumDto>();
        return album!.Id;
    }

    [Fact]
    public async Task GetAll_DoesNotExposeAnotherUsersPrivateAlbum()
    {
        var (_, aliceClient) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAlbumId = await CreateAlbumForAsync(aliceClient, "Alice Private");

        var bobList = await bobClient.GetFromJsonAsync<List<AlbumDto>>("/api/albums");

        Assert.NotNull(bobList);
        Assert.DoesNotContain(bobList!, a => a.Id == aliceAlbumId);
    }

    [Fact]
    public async Task GetById_ReturnsForbidden_WhenUserHasNoPermission()
    {
        var (_, aliceClient) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAlbumId = await CreateAlbumForAsync(aliceClient, "Alice Private");

        var response = await bobClient.GetAsync($"/api/albums/{aliceAlbumId}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task GetById_Succeeds_WhenUserHasExplicitReadPermission()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAlbumId = await CreateAlbumForAsync(aliceClient, "Shared With Bob");
        await GrantAlbumPermissionAsync(aliceAlbumId, bob.Id, alice.Id, canRead: true);

        var response = await bobClient.GetAsync($"/api/albums/{aliceAlbumId}");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var dto = await response.Content.ReadFromJsonAsync<AlbumDto>();
        Assert.NotNull(dto);
        Assert.Equal(aliceAlbumId, dto!.Id);
        Assert.False(dto.IsOwner);
        Assert.True(dto.CanRead);
        Assert.False(dto.CanWrite);
        Assert.False(dto.CanDelete);
    }

    [Fact]
    public async Task Update_ReturnsForbidden_WhenUserHasOnlyReadPermission()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAlbumId = await CreateAlbumForAsync(aliceClient, "Read Only");
        await GrantAlbumPermissionAsync(aliceAlbumId, bob.Id, alice.Id, canRead: true);

        var response = await bobClient.PutAsJsonAsync(
            $"/api/albums/{aliceAlbumId}",
            new UpdateAlbum("Bob's rename attempt"));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task Delete_ReturnsForbidden_WhenUserHasNoDeletePermission()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAlbumId = await CreateAlbumForAsync(aliceClient, "Non-deletable by Bob");
        await GrantAlbumPermissionAsync(aliceAlbumId, bob.Id, alice.Id, canRead: true, canWrite: true);

        var response = await bobClient.DeleteAsync($"/api/albums/{aliceAlbumId}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task Owner_CanUpdateAndDelete_TheirOwnAlbum()
    {
        var (_, aliceClient) = await CreateAuthenticatedUserAsync();
        var albumId = await CreateAlbumForAsync(aliceClient, "Alice Album");

        var update = await aliceClient.PutAsJsonAsync(
            $"/api/albums/{albumId}",
            new UpdateAlbum("Renamed"));
        Assert.Equal(HttpStatusCode.OK, update.StatusCode);

        var delete = await aliceClient.DeleteAsync($"/api/albums/{albumId}");
        Assert.Equal(HttpStatusCode.NoContent, delete.StatusCode);

        var get = await aliceClient.GetAsync($"/api/albums/{albumId}");
        Assert.Equal(HttpStatusCode.NotFound, get.StatusCode);
    }

    private Task GrantAlbumPermissionAsync(
        Guid albumId,
        Guid userId,
        Guid grantedByUserId,
        bool canRead = false,
        bool canWrite = false,
        bool canDelete = false,
        bool canManagePermissions = false)
    {
        return WithDbContextAsync(async db =>
        {
            db.AlbumPermissions.Add(new AlbumPermission
            {
                AlbumId = albumId,
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
