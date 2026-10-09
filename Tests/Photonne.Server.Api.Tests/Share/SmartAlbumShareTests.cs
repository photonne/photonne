using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Share;

/// <summary>
/// A public link shows the album's photos as the app does: a smart album
/// (no AlbumAssets rows) by its rule, never what is in the trash, and with
/// a password, everything once it is given, without signing in.
/// </summary>
public sealed class SmartAlbumShareTests : IntegrationTestBase
{
    public SmartAlbumShareTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record SharedPhoto(Guid Id, string ContentUrl);
    private sealed record SharedBody(bool RequiresPassword, List<SharedPhoto>? Assets);

    private const string FavoritesRule = """{"type":"favorite","value":true}""";

    [Fact]
    public async Task SmartAlbumLink_ShowsItsPhotos_ButNotTheTrashed()
    {
        var album = await SeedSmartAlbumAsync();
        var token = await CreateLinkAsync(album.AlbumId, album.OwnerId, password: null);
        var anonymous = CreateClient();

        var shared = await anonymous.GetFromJsonAsync<SharedBody>($"/api/share/{token}");

        Assert.Equal(album.Shown.OrderBy(id => id), shared!.Assets!.Select(a => a.Id).OrderBy(id => id));
        var content = await anonymous.GetAsync(shared.Assets![0].ContentUrl);
        Assert.Equal(HttpStatusCode.OK, content.StatusCode);
        var notShown = await anonymous.GetAsync($"/api/share/{token}/asset/{album.NotFavorite}/content");
        Assert.Equal(HttpStatusCode.Forbidden, notShown.StatusCode);
        var trashed = await anonymous.GetAsync($"/api/share/{token}/asset/{album.Trashed}/content");
        Assert.Equal(HttpStatusCode.Forbidden, trashed.StatusCode);
    }

    [Fact]
    public async Task PasswordLink_ShowsEverything_OnceThePasswordIsGiven()
    {
        var album = await SeedSmartAlbumAsync();
        var token = await CreateLinkAsync(album.AlbumId, album.OwnerId, password: "verano");
        var anonymous = CreateClient();

        var gate = await anonymous.GetFromJsonAsync<SharedBody>($"/api/share/{token}");
        var opened = await anonymous.GetFromJsonAsync<SharedBody>($"/api/share/{token}?pw=verano");

        Assert.True(gate!.RequiresPassword);
        Assert.Null(gate.Assets);
        Assert.Equal(album.Shown.Count, opened!.Assets!.Count);
        // The media URLs carry the password, so the photos load without a session.
        var content = await anonymous.GetAsync(opened.Assets![0].ContentUrl);
        Assert.Equal(HttpStatusCode.OK, content.StatusCode);
        var withoutPassword = await anonymous.GetAsync($"/api/share/{token}/asset/{album.Shown[0]}/content");
        Assert.NotEqual(HttpStatusCode.OK, withoutPassword.StatusCode);
    }

    private sealed record SeededAlbum(Guid AlbumId, Guid OwnerId, List<Guid> Shown, Guid NotFavorite, Guid Trashed);

    private async Task<SeededAlbum> SeedSmartAlbumAsync()
    {
        var owner = await CreateUserAsync();
        var folderPath = $"/assets/users/{owner.Username}/Camera";
        var directory = Path.Combine(Factory.InternalAssetsPath, "users", owner.Username, "Camera");
        Directory.CreateDirectory(directory);

        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = folderPath, Name = "Camera" };
            Asset Photo(string name, bool favorite, bool trashed = false)
            {
                File.Copy(FixturePaths.WithExif, Path.Combine(directory, name), overwrite: true);
                var at = new DateTime(2026, 2, 1, 10, 0, 0, DateTimeKind.Utc);
                var asset = new Asset
                {
                    FileName = name,
                    FullPath = $"{folderPath}/{name}",
                    FileSize = new FileInfo(FixturePaths.WithExif).Length,
                    Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                    Type = AssetType.Image,
                    Extension = "jpg",
                    FileCreatedAt = at,
                    FileModifiedAt = at,
                    CapturedAt = at,
                    OwnerId = owner.Id,
                    Folder = folder,
                    IsFavorite = favorite,
                    DeletedAt = trashed ? DateTime.UtcNow : null
                };
                db.Assets.Add(asset);
                return asset;
            }

            var first = Photo("a.jpg", favorite: true);
            var second = Photo("b.jpg", favorite: true);
            var other = Photo("c.jpg", favorite: false);
            var trashed = Photo("d.jpg", favorite: true, trashed: true);
            var album = new Album
            {
                Name = "Favoritas",
                OwnerId = owner.Id,
                Kind = AlbumKind.Smart,
                SmartRule = FavoritesRule
            };
            db.Albums.Add(album);
            await db.SaveChangesAsync();
            return new SeededAlbum(album.Id, owner.Id, [first.Id, second.Id], other.Id, trashed.Id);
        });
    }

    private Task<string> CreateLinkAsync(Guid albumId, Guid ownerId, string? password)
    {
        var token = Guid.NewGuid().ToString("N");
        return WithDbContextAsync(async db =>
        {
            db.SharedLinks.Add(new SharedLink
            {
                Token = token,
                AlbumId = albumId,
                CreatedById = ownerId,
                PasswordHash = password != null ? SharePasswordHasher.Hash(password) : null
            });
            await db.SaveChangesAsync();
            return token;
        });
    }
}
