using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Assets;

/// <summary>
/// Exercises the full soft-delete → restore → purge cycle against real DB state.
/// The managed library path is redirected to a per-run tmp directory (see
/// PhotonneApiFactory.InternalAssetsPath) so directory creation during the
/// delete flow doesn't need write access to /data/assets.
/// </summary>
public sealed class TrashLifecycleTests : IntegrationTestBase
{
    public TrashLifecycleTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record DeleteRequest(List<Guid> AssetIds);
    private sealed record RestoreRequest(List<Guid> AssetIds);
    private sealed record PurgeRequest(List<Guid> AssetIds);

    private async Task<Guid> CreateAssetForUserAsync(Guid userId, string fileName = "photo.jpg")
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/{userId}/{fileName}",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = userId
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    [Fact]
    public async Task Delete_SetsDeletedAt_OnOwnAsset()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetForUserAsync(alice.Id);

        var response = await aliceClient.PostAsJsonAsync(
            "/api/assets/delete",
            new DeleteRequest(new List<Guid> { assetId }));

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);

        var asset = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));

        Assert.NotNull(asset.DeletedAt);
    }

    [Fact]
    public async Task Delete_ReturnsForbidden_WhenAssetBelongsToAnotherUser()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAssetId = await CreateAssetForUserAsync(alice.Id);

        var response = await bobClient.PostAsJsonAsync(
            "/api/assets/delete",
            new DeleteRequest(new List<Guid> { aliceAssetId }));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);

        var asset = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().FirstAsync(a => a.Id == aliceAssetId));
        Assert.Null(asset.DeletedAt);
    }

    [Fact]
    public async Task Delete_RemovesAsset_FromAllAlbums()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetForUserAsync(alice.Id);

        // Arrange: create album and link the asset via DbContext (bypassing API
        // lets the test stay short and independent of upload/album endpoints).
        var albumId = await WithDbContextAsync(async db =>
        {
            var album = new Album { Name = "My Album", OwnerId = alice.Id };
            db.Albums.Add(album);
            await db.SaveChangesAsync();
            db.AlbumAssets.Add(new AlbumAsset { AlbumId = album.Id, AssetId = assetId, Order = 1 });
            await db.SaveChangesAsync();
            return album.Id;
        });

        await aliceClient.PostAsJsonAsync(
            "/api/assets/delete",
            new DeleteRequest(new List<Guid> { assetId }));

        var remaining = await WithDbContextAsync(db =>
            db.AlbumAssets.AsNoTracking().AnyAsync(aa => aa.AlbumId == albumId && aa.AssetId == assetId));

        Assert.False(remaining);
    }

    [Fact]
    public async Task Restore_ClearsDeletedAt_AndReturnsAssetToItsFolder()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetForUserAsync(alice.Id);

        var deleteResp = await aliceClient.PostAsJsonAsync(
            "/api/assets/delete",
            new DeleteRequest(new List<Guid> { assetId }));
        deleteResp.EnsureSuccessStatusCode();

        var restoreResp = await aliceClient.PostAsJsonAsync(
            "/api/assets/restore",
            new RestoreRequest(new List<Guid> { assetId }));

        Assert.Equal(HttpStatusCode.NoContent, restoreResp.StatusCode);

        var asset = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));

        Assert.Null(asset.DeletedAt);
        Assert.Null(asset.DeletedFromPath);
    }

    [Fact]
    public async Task Purge_RemovesAssetFromDatabase()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetForUserAsync(alice.Id);

        // Purge only accepts assets already in trash.
        await aliceClient.PostAsJsonAsync(
            "/api/assets/delete",
            new DeleteRequest(new List<Guid> { assetId }));

        var purgeResp = await aliceClient.PostAsJsonAsync(
            "/api/assets/purge",
            new PurgeRequest(new List<Guid> { assetId }));

        Assert.Equal(HttpStatusCode.NoContent, purgeResp.StatusCode);

        var exists = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().AnyAsync(a => a.Id == assetId));

        Assert.False(exists);
    }

    [Fact]
    public async Task EmptyTrash_PurgesOnlyCurrentUsersDeletedAssets()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAssetId = await CreateAssetForUserAsync(alice.Id);
        var bobAssetId = await CreateAssetForUserAsync(bob.Id);

        // Move both assets to their respective owner's trash.
        await aliceClient.PostAsJsonAsync("/api/assets/delete",
            new DeleteRequest(new List<Guid> { aliceAssetId }));
        await bobClient.PostAsJsonAsync("/api/assets/delete",
            new DeleteRequest(new List<Guid> { bobAssetId }));

        // Alice empties her trash; Bob's must be unaffected.
        var response = await aliceClient.PostAsync("/api/assets/trash/empty", content: null);
        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);

        var (aliceStillExists, bobStillExists) = await WithDbContextAsync(async db => (
            await db.Assets.AsNoTracking().AnyAsync(a => a.Id == aliceAssetId),
            await db.Assets.AsNoTracking().AnyAsync(a => a.Id == bobAssetId)
        ));

        Assert.False(aliceStillExists);
        Assert.True(bobStillExists);
    }
}
