using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Security;

/// <summary>
/// The media and detail endpoints answer only to someone who can read the
/// asset (owner, shared folder or library, shared album, or an admin), and a
/// stranger gets the same 404 as for an asset that doesn't exist. Browsers
/// authenticate their &lt;img&gt;/&lt;video&gt; loads with the media cookie,
/// which no other endpoint accepts.
/// </summary>
public sealed class MediaAccessTests : IntegrationTestBase
{
    public MediaAccessTests(PhotonneApiFactory factory) : base(factory) { }

    public static TheoryData<string> AssetRoutes => new()
    {
        "/api/assets/{0}",
        "/api/assets/{0}/content",
        "/api/assets/{0}/thumbnail?size=Small",
    };

    [Theory]
    [MemberData(nameof(AssetRoutes))]
    public async Task Anonymous_IsRejected(string route)
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);

        var response = await CreateClient().GetAsync(string.Format(route, assetId));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Theory]
    [MemberData(nameof(AssetRoutes))]
    public async Task AnotherUser_GetsNotFound(string route)
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);
        var (_, stranger) = await CreateAuthenticatedUserAsync();

        var response = await stranger.GetAsync(string.Format(route, assetId));

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Theory]
    [MemberData(nameof(AssetRoutes))]
    public async Task Owner_CanRead(string route)
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);
        var client = await LoginAsClientAsync(owner);

        var response = await client.GetAsync(string.Format(route, assetId));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task UserWithReadOnASharedAlbum_CanRead()
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);
        var (friend, friendClient) = await CreateAuthenticatedUserAsync();
        await WithDbContextAsync(async db =>
        {
            var album = new Album { Name = "Viaje", OwnerId = owner.Id };
            album.AlbumAssets.Add(new AlbumAsset { AssetId = assetId });
            album.Permissions.Add(new AlbumPermission { UserId = friend.Id, CanRead = true });
            db.Albums.Add(album);
            await db.SaveChangesAsync();
        });

        var response = await friendClient.GetAsync($"/api/assets/{assetId}/content");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task UserWithReadOnASharedFolder_CanRead_EvenAfterHidingItFromTheTimeline()
    {
        var owner = await CreateUserAsync();
        var (assetId, folderId) = await CreatePhotoAsync(owner, $"/assets/shared/Familia-{Guid.NewGuid():N}");
        var (member, memberClient) = await CreateAuthenticatedUserAsync();
        await WithDbContextAsync(async db =>
        {
            db.FolderPermissions.Add(new FolderPermission { UserId = member.Id, FolderId = folderId, CanRead = true });
            // Opted out of discovery: still browsable from Folders, so its media must load.
            db.Settings.Add(new Setting
            {
                OwnerId = member.Id,
                Key = AllowedFolderCache.ExcludedFoldersSettingKey,
                Value = $"[\"{folderId}\"]"
            });
            await db.SaveChangesAsync();
        });

        var response = await memberClient.GetAsync($"/api/assets/{assetId}/thumbnail?size=Small");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task Admin_CanRead()
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");

        var response = await admin.GetAsync($"/api/assets/{assetId}/thumbnail?size=Small");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task Thumbnail_IsCacheableOnlyByTheBrowser()
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);
        var client = await LoginAsClientAsync(owner);

        var response = await client.GetAsync($"/api/assets/{assetId}/thumbnail?size=Small");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(response.Headers.CacheControl?.Private);
        Assert.False(response.Headers.CacheControl?.Public);
    }

    [Fact]
    public async Task MediaCookie_FromLogin_LoadsMedia_ButNoOtherEndpoint()
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);
        var browser = await LoginWithCookiesOnlyAsync(owner);

        var content = await browser.GetAsync($"/api/assets/{assetId}/content");
        var thumbnail = await browser.GetAsync($"/api/assets/{assetId}/thumbnail?size=Small");
        var detail = await browser.GetAsync($"/api/assets/{assetId}");

        Assert.Equal(HttpStatusCode.OK, content.StatusCode);
        Assert.Equal(HttpStatusCode.OK, thumbnail.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, detail.StatusCode);
    }

    [Fact]
    public async Task Logout_DropsTheMediaCookie()
    {
        var owner = await CreateUserAsync();
        var assetId = await CreatePhotoAsync(owner);
        var browser = await LoginWithCookiesOnlyAsync(owner);

        (await browser.PostAsync("/api/auth/logout", null)).EnsureSuccessStatusCode();
        var response = await browser.GetAsync($"/api/assets/{assetId}/content");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task MediaSession_SetsTheCookie_FromTheBearerToken()
    {
        var owner = await CreateUserAsync();
        var client = await LoginAsClientAsync(owner);

        var response = await client.PostAsync("/api/auth/media-session", null);

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        var cookie = Assert.Single(response.Headers.GetValues("Set-Cookie"));
        Assert.StartsWith(MediaSessionCookie.Name + "=", cookie);
        Assert.Contains("httponly", cookie, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("samesite=strict", cookie, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("path=/api", cookie, StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>A client that behaves like a browser &lt;img&gt;: cookies, never a header.</summary>
    private async Task<HttpClient> LoginWithCookiesOnlyAsync(TestUser user)
    {
        var client = Factory.CreateClient(new WebApplicationFactoryClientOptions { HandleCookies = true });
        var response = await client.PostAsJsonAsync("/api/auth/login", new
        {
            Username = user.Username,
            Password = user.Password,
            DeviceId = Guid.NewGuid().ToString("N")
        });
        response.EnsureSuccessStatusCode();
        Assert.Null(client.DefaultRequestHeaders.Authorization);
        return client;
    }

    private async Task<Guid> CreatePhotoAsync(TestUser owner) =>
        (await CreatePhotoAsync(owner, $"/assets/users/{owner.Username}/Camera")).AssetId;

    /// <summary>Copies a real JPEG under the virtual folder path and indexes it.</summary>
    private async Task<(Guid AssetId, Guid FolderId)> CreatePhotoAsync(TestUser owner, string folderPath)
    {
        var relative = folderPath["/assets/".Length..].Replace('/', Path.DirectorySeparatorChar);
        var directory = Path.Combine(Factory.InternalAssetsPath, relative);
        Directory.CreateDirectory(directory);
        File.Copy(FixturePaths.WithExif, Path.Combine(directory, "photo.jpg"), overwrite: true);

        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = folderPath, Name = folderPath.Split('/').Last() };
            var capturedAt = new DateTime(2026, 2, 1, 10, 0, 0, DateTimeKind.Utc);
            var asset = new Asset
            {
                FileName = "photo.jpg",
                FullPath = $"{folderPath}/photo.jpg",
                FileSize = new FileInfo(FixturePaths.WithExif).Length,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                Folder = folder
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return (asset.Id, folder.Id);
        });
    }
}
