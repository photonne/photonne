using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Users;

/// <summary>
/// POST /api/users/me/delete-account is the in-app account deletion the stores
/// require. It must confirm the password, protect the primary admin and not trip
/// on the Restrict FK from albums to their owner.
/// </summary>
public sealed class DeleteMyAccountTests : IntegrationTestBase
{
    public DeleteMyAccountTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record DeleteAccountReq(string Password);

    [Fact]
    public async Task DeleteMyAccount_RemovesUser_EvenWithOwnedAlbums()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        await WithDbContextAsync(async db =>
        {
            db.Albums.Add(new Album { Name = "Mine", OwnerId = user.Id });
            await db.SaveChangesAsync();
        });

        var response = await client.PostAsJsonAsync("/api/users/me/delete-account", new DeleteAccountReq(user.Password));

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        var (userLeft, albumsLeft) = await WithDbContextAsync(async db => (
            await db.Users.AnyAsync(u => u.Id == user.Id),
            await db.Albums.AnyAsync(a => a.OwnerId == user.Id)));
        Assert.False(userLeft);
        Assert.False(albumsLeft);
    }

    [Fact]
    public async Task DeleteMyAccount_WithWrongPassword_KeepsUser()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        var response = await client.PostAsJsonAsync("/api/users/me/delete-account", new DeleteAccountReq("wrong-password"));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.True(await WithDbContextAsync(db => db.Users.AnyAsync(u => u.Id == user.Id)));
    }

    [Fact]
    public async Task DeleteMyAccount_IsRejected_ForPrimaryAdmin()
    {
        var (user, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        await WithDbContextAsync(async db =>
        {
            var dbUser = await db.Users.SingleAsync(u => u.Id == user.Id);
            dbUser.IsPrimaryAdmin = true;
            await db.SaveChangesAsync();
        });

        var response = await client.PostAsJsonAsync("/api/users/me/delete-account", new DeleteAccountReq(user.Password));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.True(await WithDbContextAsync(db => db.Users.AnyAsync(u => u.Id == user.Id)));
    }
}
