using System.Net;
using System.Net.Http.Json;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Albums;

/// <summary>
/// Sharing an album grants read on what it holds, manual or smart alike. A
/// smart album's members are its rule resolved as the OWNER sees it,
/// materialized as AlbumAsset rows, so a reader sees them — media included —
/// even without access to where they came from (a shared folder they aren't
/// in). And since adding a photo to an album shares it, only a photo the
/// caller can read may be added.
/// </summary>
public sealed class SmartAlbumSharingTests : IntegrationTestBase
{
    public SmartAlbumSharingTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record AlbumItem(Guid Id, int AssetCount, string Kind);
    private sealed record AssetItem(Guid Id);
    private sealed record BatchResult(int Added, int Skipped);

    /// <summary>A real JPEG, owned by <paramref name="owner"/>, in a new folder at <paramref name="folderPath"/>.</summary>
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

    private Task GrantFolderAsync(Guid folderId, Guid userId) =>
        WithDbContextAsync(async db =>
        {
            db.FolderPermissions.Add(new FolderPermission { FolderId = folderId, UserId = userId, CanRead = true });
            await db.SaveChangesAsync();
        });

    private Task ShareAlbumAsync(Guid albumId, Guid ownerId, Guid userId) =>
        WithDbContextAsync(async db =>
        {
            db.AlbumPermissions.Add(new AlbumPermission
            {
                AlbumId = albumId, UserId = userId, GrantedByUserId = ownerId, CanRead = true
            });
            await db.SaveChangesAsync();
        });

    private static async Task<Guid> CreateSmartAlbumAsync(HttpClient client, Guid folderId)
    {
        var resp = await client.PostAsJsonAsync("/api/albums", new
        {
            name = "Familia",
            smartRule = new { type = "folder", folderIds = new[] { folderId }, includeSubfolders = true }
        });
        resp.EnsureSuccessStatusCode();
        return (await resp.Content.ReadFromJsonAsync<AlbumItem>())!.Id;
    }

    private static async Task<List<Guid>> AlbumAssetIdsAsync(HttpClient client, Guid albumId)
    {
        var items = await client.GetFromJsonAsync<List<AssetItem>>($"/api/albums/{albumId}/assets");
        return items!.Select(i => i.Id).ToList();
    }

    /// <summary>
    /// carol owns a photo in a shared folder; alice can read the folder, bob
    /// can't. alice builds a smart album on the folder and shares it with bob.
    /// </summary>
    private async Task<(TestUser Alice, HttpClient AliceClient, TestUser Bob, HttpClient BobClient,
        Guid AssetId, Guid FolderId, Guid AlbumId)> SeedSharedSmartAlbumAsync()
    {
        var carol = await CreateUserAsync();
        var (assetId, folderId) = await CreatePhotoAsync(carol, $"/assets/shared/Familia-{Guid.NewGuid():N}");
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        await GrantFolderAsync(folderId, alice.Id);

        var albumId = await CreateSmartAlbumAsync(aliceClient, folderId);
        await ShareAlbumAsync(albumId, alice.Id, bob.Id);
        return (alice, aliceClient, bob, bobClient, assetId, folderId, albumId);
    }

    [Fact]
    public async Task SharedSmartAlbum_ShowsItsPhotos_ToAReaderOutsideTheSourceFolder()
    {
        var s = await SeedSharedSmartAlbumAsync();

        Assert.Equal(new[] { s.AssetId }, await AlbumAssetIdsAsync(s.BobClient, s.AlbumId));

        var listed = (await s.BobClient.GetFromJsonAsync<List<AlbumItem>>("/api/albums"))!
            .Single(a => a.Id == s.AlbumId);
        Assert.Equal(1, listed.AssetCount);

        var thumbnail = await s.BobClient.GetAsync($"/api/assets/{s.AssetId}/thumbnail?size=Small");
        Assert.Equal(HttpStatusCode.OK, thumbnail.StatusCode);
        var content = await s.BobClient.GetAsync($"/api/assets/{s.AssetId}/content");
        Assert.Equal(HttpStatusCode.OK, content.StatusCode);
    }

    [Fact]
    public async Task SharedSmartAlbum_GrantsNothingToSomeoneItIsNotSharedWith()
    {
        var s = await SeedSharedSmartAlbumAsync();
        var (_, stranger) = await CreateAuthenticatedUserAsync();

        var content = await stranger.GetAsync($"/api/assets/{s.AssetId}/content");

        Assert.Equal(HttpStatusCode.NotFound, content.StatusCode);
    }

    [Fact]
    public async Task OwnerLosingAccess_TakesThePhotoOutOfTheAlbum_AndAwayFromItsReaders()
    {
        var s = await SeedSharedSmartAlbumAsync();
        await WithDbContextAsync(async db =>
        {
            db.FolderPermissions.RemoveRange(db.FolderPermissions.Where(p => p.UserId == s.Alice.Id));
            await db.SaveChangesAsync();
        });
        Factory.Services.GetRequiredService<AllowedFolderCache>().Invalidate(s.Alice.Id);

        // Opening the album refreshes it — whoever opens it, as the owner sees it.
        Assert.Empty(await AlbumAssetIdsAsync(s.BobClient, s.AlbumId));

        var content = await s.BobClient.GetAsync($"/api/assets/{s.AssetId}/content");
        Assert.Equal(HttpStatusCode.NotFound, content.StatusCode);
    }

    [Fact]
    public async Task SmartAlbums_DoNotFeedEachOther()
    {
        var s = await SeedSharedSmartAlbumAsync();

        // bob reaches the photo only through alice's smart album: his own rule
        // over the same folder doesn't pick it up.
        var bobsAlbum = await CreateSmartAlbumAsync(s.BobClient, s.FolderId);

        Assert.Empty(await AlbumAssetIdsAsync(s.BobClient, bobsAlbum));
    }

    [Fact]
    public async Task SmartAlbum_RejectsManualAddAndRemove()
    {
        var s = await SeedSharedSmartAlbumAsync();

        var add = await s.AliceClient.PostAsJsonAsync($"/api/albums/{s.AlbumId}/assets", new { assetId = s.AssetId });
        Assert.Equal(HttpStatusCode.BadRequest, add.StatusCode);
        var batch = await s.AliceClient.PostAsJsonAsync($"/api/albums/{s.AlbumId}/assets/batch", new { assetIds = new[] { s.AssetId } });
        Assert.Equal(HttpStatusCode.BadRequest, batch.StatusCode);
        var remove = await s.AliceClient.DeleteAsync($"/api/albums/{s.AlbumId}/assets/{s.AssetId}");
        Assert.Equal(HttpStatusCode.BadRequest, remove.StatusCode);

        Assert.Equal(new[] { s.AssetId }, await AlbumAssetIdsAsync(s.AliceClient, s.AlbumId));
    }

    [Fact]
    public async Task ManualAlbum_AcceptsOnlyPhotosTheCallerCanRead()
    {
        var carol = await CreateUserAsync();
        var (carolsPhoto, _) = await CreatePhotoAsync(carol, $"/assets/users/{carol.Username}/Camera");
        var (mallory, malloryClient) = await CreateAuthenticatedUserAsync();
        var (ownPhoto, _) = await CreatePhotoAsync(mallory, $"/assets/users/{mallory.Username}/Camera");

        var created = await malloryClient.PostAsJsonAsync("/api/albums", new { name = "Botín" });
        var albumId = (await created.Content.ReadFromJsonAsync<AlbumItem>())!.Id;

        var single = await malloryClient.PostAsJsonAsync($"/api/albums/{albumId}/assets", new { assetId = carolsPhoto });
        Assert.Equal(HttpStatusCode.NotFound, single.StatusCode);

        var batch = await malloryClient.PostAsJsonAsync($"/api/albums/{albumId}/assets/batch",
            new { assetIds = new[] { carolsPhoto, ownPhoto } });
        batch.EnsureSuccessStatusCode();
        Assert.Equal(new BatchResult(1, 1), await batch.Content.ReadFromJsonAsync<BatchResult>());

        Assert.Equal(new[] { ownPhoto }, await AlbumAssetIdsAsync(malloryClient, albumId));
        var content = await malloryClient.GetAsync($"/api/assets/{carolsPhoto}/content");
        Assert.Equal(HttpStatusCode.NotFound, content.StatusCode);
    }
}
