using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Share;

/// <summary>
/// Public /api/share/{token} gates visibility on expiration, view caps and
/// optional password. These tests lock down the HTTP status contract each
/// branch returns, since a regression here would silently leak content.
/// </summary>
public sealed class SharedLinkTests : IntegrationTestBase
{
    public SharedLinkTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record ShareResp(string Token, bool RequiresPassword, bool WrongPassword);

    private async Task<(Guid albumId, Guid ownerId)> CreateAlbumForNewUserAsync()
    {
        var owner = await CreateUserAsync();
        var albumId = await WithDbContextAsync(async db =>
        {
            var album = new Album { Name = "Shared album", OwnerId = owner.Id };
            db.Albums.Add(album);
            await db.SaveChangesAsync();
            return album.Id;
        });
        return (albumId, owner.Id);
    }

    private Task<string> CreateShareLinkAsync(
        Guid albumId,
        Guid ownerId,
        DateTime? expiresAt = null,
        int? maxViews = null,
        string? password = null)
    {
        var token = Guid.NewGuid().ToString("N");
        return WithDbContextAsync(async db =>
        {
            db.SharedLinks.Add(new SharedLink
            {
                Token = token,
                AlbumId = albumId,
                CreatedById = ownerId,
                ExpiresAt = expiresAt,
                MaxViews = maxViews,
                PasswordHash = password != null ? SharePasswordHasher.Hash(password) : null
            });
            await db.SaveChangesAsync();
            return token;
        });
    }

    [Fact]
    public async Task UnknownToken_Returns404()
    {
        var client = CreateClient();
        var response = await client.GetAsync($"/api/share/{Guid.NewGuid():N}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task ExpiredLink_Returns410()
    {
        var (albumId, ownerId) = await CreateAlbumForNewUserAsync();
        var token = await CreateShareLinkAsync(albumId, ownerId,
            expiresAt: DateTime.UtcNow.AddMinutes(-1));

        var client = CreateClient();
        var response = await client.GetAsync($"/api/share/{token}");

        Assert.Equal(HttpStatusCode.Gone, response.StatusCode);
    }

    [Fact]
    public async Task LinkAtMaxViews_Returns410()
    {
        var (albumId, ownerId) = await CreateAlbumForNewUserAsync();
        var token = await CreateShareLinkAsync(albumId, ownerId, maxViews: 2);

        var client = CreateClient();
        (await client.GetAsync($"/api/share/{token}")).EnsureSuccessStatusCode();
        (await client.GetAsync($"/api/share/{token}")).EnsureSuccessStatusCode();

        var third = await client.GetAsync($"/api/share/{token}");
        Assert.Equal(HttpStatusCode.Gone, third.StatusCode);
    }

    [Fact]
    public async Task PasswordProtectedLink_WithoutPassword_ReturnsPasswordGate()
    {
        var (albumId, ownerId) = await CreateAlbumForNewUserAsync();
        var token = await CreateShareLinkAsync(albumId, ownerId, password: "hunter2");

        var client = CreateClient();
        var response = await client.GetAsync($"/api/share/{token}");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<ShareResp>();
        Assert.NotNull(body);
        Assert.True(body!.RequiresPassword);
        Assert.False(body.WrongPassword);
    }

    [Fact]
    public async Task PasswordProtectedLink_WithWrongPassword_ReturnsGateWithFlag()
    {
        var (albumId, ownerId) = await CreateAlbumForNewUserAsync();
        var token = await CreateShareLinkAsync(albumId, ownerId, password: "hunter2");

        var client = CreateClient();
        var response = await client.GetAsync($"/api/share/{token}?pw=nope");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<ShareResp>();
        Assert.NotNull(body);
        Assert.True(body!.RequiresPassword);
        Assert.True(body.WrongPassword);
    }

    [Fact]
    public async Task ValidAccess_IncrementsViewCount()
    {
        var (albumId, ownerId) = await CreateAlbumForNewUserAsync();
        var token = await CreateShareLinkAsync(albumId, ownerId);

        var client = CreateClient();
        (await client.GetAsync($"/api/share/{token}")).EnsureSuccessStatusCode();
        (await client.GetAsync($"/api/share/{token}")).EnsureSuccessStatusCode();

        var viewCount = await WithDbContextAsync(db =>
        {
            var link = db.SharedLinks.Single(l => l.Token == token);
            return Task.FromResult(link.ViewCount);
        });

        Assert.Equal(2, viewCount);
    }

    [Fact]
    public async Task NoAuthenticationRequired_ForPublicShareLink()
    {
        // /api/share/{token} is intentionally anonymous. Regressions have
        // previously required login and silently broke public links.
        var (albumId, ownerId) = await CreateAlbumForNewUserAsync();
        var token = await CreateShareLinkAsync(albumId, ownerId);

        var client = CreateClient();
        Assert.Null(client.DefaultRequestHeaders.Authorization);

        var response = await client.GetAsync($"/api/share/{token}");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }
}
