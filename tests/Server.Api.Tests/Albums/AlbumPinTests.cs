using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Albums;

/// <summary>
/// Pins are personal: PUT/DELETE /api/albums/{id}/pin only touch the caller's
/// own row, are idempotent, and 404 for albums the caller can't see.
/// </summary>
public sealed class AlbumPinTests : IntegrationTestBase
{
    public AlbumPinTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record AlbumDto(Guid Id, string Name, bool IsOwner, bool IsPinned, DateTime? PinnedAt);
    private sealed record PinDto(Guid AlbumId, bool IsPinned, DateTime? PinnedAt);
    private sealed record CreateAlbum(string Name, string? Description = null);

    private static async Task<Guid> CreateAlbumForAsync(HttpClient client, string name)
    {
        var response = await client.PostAsJsonAsync("/api/albums", new CreateAlbum(name));
        response.EnsureSuccessStatusCode();
        var album = await response.Content.ReadFromJsonAsync<AlbumDto>();
        return album!.Id;
    }

    private static async Task<AlbumDto> FindInListAsync(HttpClient client, Guid albumId)
    {
        var list = await client.GetFromJsonAsync<List<AlbumDto>>("/api/albums");
        Assert.NotNull(list);
        return Assert.Single(list!, a => a.Id == albumId);
    }

    [Fact]
    public async Task Pin_And_Unpin_AreIdempotent_AndShowInListAndDetail()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var albumId = await CreateAlbumForAsync(aliceClient, "Pinned");

        Assert.False((await FindInListAsync(aliceClient, albumId)).IsPinned);

        var first = await aliceClient.PutAsync($"/api/albums/{albumId}/pin", null);
        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        var firstPin = await first.Content.ReadFromJsonAsync<PinDto>();
        Assert.True(firstPin!.IsPinned);
        Assert.NotNull(firstPin.PinnedAt);

        // Second PUT keeps the original pin instead of failing or re-stamping it.
        var second = await aliceClient.PutAsync($"/api/albums/{albumId}/pin", null);
        Assert.Equal(HttpStatusCode.OK, second.StatusCode);
        var secondPin = await second.Content.ReadFromJsonAsync<PinDto>();
        Assert.Equal(firstPin.PinnedAt, secondPin!.PinnedAt);

        var rows = await WithDbContextAsync(db =>
            db.AlbumPins.CountAsync(p => p.AlbumId == albumId && p.UserId == alice.Id));
        Assert.Equal(1, rows);

        var listed = await FindInListAsync(aliceClient, albumId);
        Assert.True(listed.IsPinned);
        Assert.NotNull(listed.PinnedAt);
        var detail = await aliceClient.GetFromJsonAsync<AlbumDto>($"/api/albums/{albumId}");
        Assert.True(detail!.IsPinned);

        var unpin = await aliceClient.DeleteAsync($"/api/albums/{albumId}/pin");
        Assert.Equal(HttpStatusCode.OK, unpin.StatusCode);
        var unpinAgain = await aliceClient.DeleteAsync($"/api/albums/{albumId}/pin");
        Assert.Equal(HttpStatusCode.OK, unpinAgain.StatusCode);

        var afterUnpin = await FindInListAsync(aliceClient, albumId);
        Assert.False(afterUnpin.IsPinned);
        Assert.Null(afterUnpin.PinnedAt);
    }

    [Fact]
    public async Task MemberPinningSharedAlbum_DoesNotPinItForTheOwner()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var albumId = await CreateAlbumForAsync(aliceClient, "Shared");
        await WithDbContextAsync(async db =>
        {
            db.AlbumPermissions.Add(new AlbumPermission
            {
                AlbumId = albumId, UserId = bob.Id, GrantedByUserId = alice.Id, CanRead = true
            });
            await db.SaveChangesAsync();
        });

        var pin = await bobClient.PutAsync($"/api/albums/{albumId}/pin", null);
        Assert.Equal(HttpStatusCode.OK, pin.StatusCode);

        Assert.True((await FindInListAsync(bobClient, albumId)).IsPinned);
        Assert.False((await FindInListAsync(aliceClient, albumId)).IsPinned);
        var ownerDetail = await aliceClient.GetFromJsonAsync<AlbumDto>($"/api/albums/{albumId}");
        Assert.False(ownerDetail!.IsPinned);

        // Leaving the album drops the member's pin with it.
        var leave = await bobClient.PostAsync($"/api/albums/{albumId}/leave", null);
        Assert.Equal(HttpStatusCode.NoContent, leave.StatusCode);
        var remaining = await WithDbContextAsync(db => db.AlbumPins.CountAsync(p => p.AlbumId == albumId));
        Assert.Equal(0, remaining);
    }

    [Fact]
    public async Task Pin_ReturnsNotFound_ForAlbumTheUserCannotSee()
    {
        var (_, aliceClient) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        var albumId = await CreateAlbumForAsync(aliceClient, "Private");

        Assert.Equal(HttpStatusCode.NotFound,
            (await bobClient.PutAsync($"/api/albums/{albumId}/pin", null)).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,
            (await bobClient.DeleteAsync($"/api/albums/{albumId}/pin")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,
            (await bobClient.PutAsync($"/api/albums/{Guid.NewGuid()}/pin", null)).StatusCode);
    }

    [Fact]
    public async Task DeletingAlbum_CascadesItsPins()
    {
        var (_, aliceClient) = await CreateAuthenticatedUserAsync();
        var albumId = await CreateAlbumForAsync(aliceClient, "Doomed");
        (await aliceClient.PutAsync($"/api/albums/{albumId}/pin", null)).EnsureSuccessStatusCode();

        (await aliceClient.DeleteAsync($"/api/albums/{albumId}")).EnsureSuccessStatusCode();

        var remaining = await WithDbContextAsync(db => db.AlbumPins.CountAsync(p => p.AlbumId == albumId));
        Assert.Equal(0, remaining);
    }
}
