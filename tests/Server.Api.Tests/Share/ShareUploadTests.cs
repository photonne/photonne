using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Share;

/// <summary>
/// Public POST /api/share/{token}/upload — the "solicitud de fotos" flow where
/// anonymous visitors add photos to a shared album. The asset must land under
/// the LINK CREATOR's PhotoRequests folder with the creator as owner, and the
/// gate (AllowUpload + expiry + password) must hold, since a hole here means
/// anyone can write into a user's library.
/// </summary>
public sealed class ShareUploadTests : IntegrationTestBase
{
    public ShareUploadTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<(TestUser owner, Guid albumId, string token)> CreateUploadLinkAsync(
        bool allowUpload = true,
        DateTime? expiresAt = null,
        string? password = null,
        string albumName = "Boda 2026")
    {
        var owner = await CreateUserAsync();
        var token = Guid.NewGuid().ToString("N");
        var albumId = await WithDbContextAsync(async db =>
        {
            var album = new Album { Name = albumName, OwnerId = owner.Id };
            db.Albums.Add(album);
            db.SharedLinks.Add(new SharedLink
            {
                Token = token,
                Album = album,
                CreatedById = owner.Id,
                AllowUpload = allowUpload,
                ExpiresAt = expiresAt,
                PasswordHash = password != null ? SharePasswordHasher.Hash(password) : null
            });
            await db.SaveChangesAsync();
            return album.Id;
        });
        return (owner, albumId, token);
    }

    private static MultipartFormDataContent BuildMultipart(
        string fixturePath, string? uploaderName = null, string? pw = null)
    {
        var fileContent = new ByteArrayContent(File.ReadAllBytes(fixturePath));
        fileContent.Headers.ContentType = MediaTypeHeaderValue.Parse("image/jpeg");
        var form = new MultipartFormDataContent
        {
            { fileContent, "file", Path.GetFileName(fixturePath) }
        };
        if (uploaderName != null) form.Add(new StringContent(uploaderName), "uploaderName");
        if (pw != null) form.Add(new StringContent(pw), "pw");
        return form;
    }

    [Fact]
    public async Task GuestUpload_CreatesOwnerAssetInPhotoRequests_AndAddsItToTheAlbum()
    {
        var (owner, albumId, token) = await CreateUploadLinkAsync();

        var client = CreateClient(); // sin autenticación: invitado
        using var form = BuildMultipart(FixturePaths.WithExif);
        var response = await client.PostAsync($"/api/share/{token}/upload", form);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.AsNoTracking().SingleAsync();
            Assert.Equal(owner.Id, asset.OwnerId);
            Assert.StartsWith($"/assets/users/{owner.Username}/PhotoRequests/Boda_2026/", asset.FullPath);

            var inAlbum = await db.AlbumAssets.AsNoTracking()
                .AnyAsync(aa => aa.AlbumId == albumId && aa.AssetId == asset.Id);
            Assert.True(inAlbum);

            var link = await db.SharedLinks.AsNoTracking().SingleAsync(l => l.Token == token);
            Assert.Equal(1, link.UploadCount);

            // First upload notifies the owner (milestone 1).
            var notified = await db.Notifications.AsNoTracking()
                .AnyAsync(n => n.UserId == owner.Id && n.Type == NotificationType.ShareUploaded);
            Assert.True(notified);

            // Enrichment queued like any regular upload.
            var tasks = await db.AssetEnrichmentTasks.AsNoTracking()
                .Where(t => t.AssetId == asset.Id).ToListAsync();
            Assert.Contains(tasks, t => t.TaskType == AssetEnrichmentType.Thumbnails);
        });
    }

    [Fact]
    public async Task GuestUpload_WithUploaderName_LandsInGuestSubfolder()
    {
        var (owner, _, token) = await CreateUploadLinkAsync();

        var client = CreateClient();
        using var form = BuildMultipart(FixturePaths.NoMetadata, uploaderName: "John Smith");
        var response = await client.PostAsync($"/api/share/{token}/upload", form);
        response.EnsureSuccessStatusCode();

        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.AsNoTracking().SingleAsync();
            Assert.StartsWith(
                $"/assets/users/{owner.Username}/PhotoRequests/Boda_2026/John_Smith/",
                asset.FullPath);
        });
    }

    [Fact]
    public async Task GuestUpload_WithoutAllowUpload_Returns403AndWritesNothing()
    {
        var (_, _, token) = await CreateUploadLinkAsync(allowUpload: false);

        var client = CreateClient();
        using var form = BuildMultipart(FixturePaths.NoMetadata);
        var response = await client.PostAsync($"/api/share/{token}/upload", form);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        var count = await WithDbContextAsync(db => db.Assets.AsNoTracking().CountAsync());
        Assert.Equal(0, count);
    }

    [Fact]
    public async Task GuestUpload_ExpiredLink_Returns410()
    {
        var (_, _, token) = await CreateUploadLinkAsync(
            expiresAt: DateTime.UtcNow.AddMinutes(-1));

        var client = CreateClient();
        using var form = BuildMultipart(FixturePaths.NoMetadata);
        var response = await client.PostAsync($"/api/share/{token}/upload", form);

        Assert.Equal(HttpStatusCode.Gone, response.StatusCode);
    }

    [Fact]
    public async Task GuestUpload_PasswordProtected_RequiresTheCorrectPassword()
    {
        var (_, _, token) = await CreateUploadLinkAsync(password: "hunter2");

        var client = CreateClient();
        using (var wrong = BuildMultipart(FixturePaths.NoMetadata, pw: "nope"))
        {
            var denied = await client.PostAsync($"/api/share/{token}/upload", wrong);
            Assert.Equal(HttpStatusCode.Unauthorized, denied.StatusCode);
        }

        using var right = BuildMultipart(FixturePaths.NoMetadata, pw: "hunter2");
        var response = await client.PostAsync($"/api/share/{token}/upload", right);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task GuestUpload_UnknownToken_Returns404()
    {
        var client = CreateClient();
        using var form = BuildMultipart(FixturePaths.NoMetadata);
        var response = await client.PostAsync($"/api/share/{Guid.NewGuid():N}/upload", form);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task GuestUpload_DuplicateFile_ShortCircuitsButStaysInAlbum()
    {
        var (_, albumId, token) = await CreateUploadLinkAsync();

        var client = CreateClient();
        using (var first = BuildMultipart(FixturePaths.WithExif))
        {
            (await client.PostAsync($"/api/share/{token}/upload", first)).EnsureSuccessStatusCode();
        }

        using var second = BuildMultipart(FixturePaths.WithExif);
        var response = await client.PostAsync($"/api/share/{token}/upload", second);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync();
        Assert.Contains("already exists", body, StringComparison.OrdinalIgnoreCase);

        await WithDbContextAsync(async db =>
        {
            Assert.Equal(1, await db.Assets.AsNoTracking().CountAsync());
            Assert.Equal(1, await db.AlbumAssets.AsNoTracking()
                .CountAsync(aa => aa.AlbumId == albumId));
        });
    }

    private sealed record UploadedAsset(Guid Id, string FileName, string ThumbnailUrl, string ContentUrl);
    private sealed record UploadBody(string Message, UploadedAsset? Asset);
    private sealed record ShareBody(List<UploadedAsset>? Assets);

    [Fact]
    public async Task GuestUpload_ReturnsThePhotoAsTheLinkPageListsIt_WithoutCountingAVisit()
    {
        var (_, _, token) = await CreateUploadLinkAsync(password: "hunter2");

        var client = CreateClient();
        using var form = BuildMultipart(FixturePaths.WithExif, pw: "hunter2");
        var response = await client.PostAsync($"/api/share/{token}/upload", form);
        response.EnsureSuccessStatusCode();
        var uploaded = (await response.Content.ReadFromJsonAsync<UploadBody>())!.Asset;

        Assert.NotNull(uploaded);
        Assert.Equal(0, await WithDbContextAsync(db =>
            db.SharedLinks.AsNoTracking().Where(l => l.Token == token).Select(l => l.ViewCount).SingleAsync()));

        // The same entry the page gets when it opens the link, password included.
        var page = await client.GetFromJsonAsync<ShareBody>($"/api/share/{token}?pw=hunter2");
        Assert.Equal(page!.Assets!.Single(), uploaded);
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync(uploaded!.ThumbnailUrl)).StatusCode);
    }

    private async Task<Guid> SeedAssetWithFixtureChecksumAsync(TestUser user, DateTime? deletedAt = null)
    {
        var checksum = await new FileHashService().CalculateFileHashAsync(FixturePaths.WithExif);
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = "theirs.jpg",
                FullPath = $"/assets/users/{user.Username}/theirs.jpg",
                FileSize = 1,
                Checksum = checksum,
                Type = AssetType.Image,
                Extension = ".jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = user.Id,
                DeletedAt = deletedAt
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    [Fact]
    public async Task GuestUpload_DuplicateOfAnotherUsersPhoto_IsStoredForTheOwner_AndSaysNothingAboutIt()
    {
        var (owner, albumId, token) = await CreateUploadLinkAsync();
        var stranger = await CreateUserAsync();
        var theirs = await SeedAssetWithFixtureChecksumAsync(stranger);

        var client = CreateClient();
        using var form = BuildMultipart(FixturePaths.WithExif);
        var response = await client.PostAsync($"/api/share/{token}/upload", form);
        response.EnsureSuccessStatusCode();

        // The same answer as a file nobody had: whoever holds the link can't
        // tell that another library has it.
        var body = await response.Content.ReadFromJsonAsync<UploadBody>();
        Assert.Equal("Asset uploaded successfully", body!.Message);
        Assert.NotNull(body.Asset);
        Assert.NotEqual(theirs, body.Asset!.Id);

        await WithDbContextAsync(async db =>
        {
            var stored = await db.Assets.AsNoTracking().SingleAsync(a => a.Id == body.Asset.Id);
            Assert.Equal(owner.Id, stored.OwnerId);
            var inAlbum = await db.AlbumAssets.AsNoTracking()
                .Where(aa => aa.AlbumId == albumId).Select(aa => aa.AssetId).ToListAsync();
            Assert.Equal([stored.Id], inAlbum);
        });
    }

    [Fact]
    public async Task GuestUpload_DuplicateOfAPhotoTheOwnerTrashed_IsStoredAgain()
    {
        var (owner, albumId, token) = await CreateUploadLinkAsync();
        var trashed = await SeedAssetWithFixtureChecksumAsync(owner, deletedAt: DateTime.UtcNow);

        var client = CreateClient();
        using var form = BuildMultipart(FixturePaths.WithExif);
        var response = await client.PostAsync($"/api/share/{token}/upload", form);
        response.EnsureSuccessStatusCode();

        var body = await response.Content.ReadFromJsonAsync<UploadBody>();
        Assert.NotNull(body!.Asset);
        Assert.NotEqual(trashed, body.Asset!.Id);
        Assert.Equal(1, await WithDbContextAsync(db =>
            db.AlbumAssets.AsNoTracking().CountAsync(aa => aa.AlbumId == albumId)));
    }
}
