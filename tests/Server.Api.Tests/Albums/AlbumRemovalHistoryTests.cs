using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Albums;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Albums;

/// <summary>
/// Taking photos out of an album keeps a record of each for a while: the
/// album lists what was removed, by whom and when, and can put it back in
/// its place — so a removal that went by unnoticed can still be undone.
/// </summary>
public sealed class AlbumRemovalHistoryTests : IntegrationTestBase
{
    public AlbumRemovalHistoryTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record AlbumDto(Guid Id, int AssetCount, string? CoverThumbnailUrl);
    private sealed record AssetItem(Guid Id);
    private sealed record RemovedItem(AssetItem Asset, DateTime RemovedAt, Guid? RemovedByUserId, string? RemovedByUsername);
    private sealed record RemoveResult(List<Guid> Removed, int Skipped);
    private sealed record RestoreResult(List<Guid> Restored, int Skipped);

    private static readonly DateTime FirstAddedAt = new(2026, 3, 1, 9, 0, 0, DateTimeKind.Utc);

    /// <summary>
    /// A manual album of <paramref name="owner"/> holding <paramref name="count"/>
    /// photos in order, added a minute apart, the first one as cover.
    /// </summary>
    private async Task<(Guid AlbumId, List<Guid> AssetIds)> CreateAlbumWithPhotosAsync(TestUser owner, int count)
    {
        return await WithDbContextAsync(async db =>
        {
            var album = new Album { Name = "Viaje", OwnerId = owner.Id };
            db.Albums.Add(album);
            var ids = new List<Guid>();
            for (var i = 0; i < count; i++)
            {
                var asset = new Asset
                {
                    FileName = $"photo{i}.jpg",
                    FullPath = $"/assets/users/{owner.Username}/photo{i}.jpg",
                    FileSize = 1000,
                    Checksum = Guid.NewGuid().ToString("N"),
                    Type = AssetType.Image,
                    Extension = "jpg",
                    FileCreatedAt = FirstAddedAt,
                    FileModifiedAt = FirstAddedAt,
                    OwnerId = owner.Id
                };
                db.Assets.Add(asset);
                db.AlbumAssets.Add(new AlbumAsset
                {
                    AlbumId = album.Id, AssetId = asset.Id, Order = i + 1, AddedAt = FirstAddedAt.AddMinutes(i)
                });
                ids.Add(asset.Id);
            }
            album.CoverAssetId = ids[0];
            await db.SaveChangesAsync();
            return (album.Id, ids);
        });
    }

    private static async Task<List<Guid>> AlbumContentsAsync(HttpClient client, Guid albumId)
    {
        var items = await client.GetFromJsonAsync<List<AssetItem>>($"/api/albums/{albumId}/assets");
        return items!.Select(i => i.Id).ToList();
    }

    private static Task<HttpResponseMessage> RemoveAsync(HttpClient client, Guid albumId, params Guid[] ids) =>
        client.PostAsJsonAsync($"/api/albums/{albumId}/assets/remove", new { assetIds = ids });

    private static Task<HttpResponseMessage> RestoreAsync(HttpClient client, Guid albumId, params Guid[] ids) =>
        client.PostAsJsonAsync($"/api/albums/{albumId}/assets/restore", new { assetIds = ids });

    private Task GrantAsync(Guid albumId, Guid ownerId, Guid userId, bool canWrite) =>
        WithDbContextAsync(async db =>
        {
            db.AlbumPermissions.Add(new AlbumPermission
            {
                AlbumId = albumId, UserId = userId, GrantedByUserId = ownerId, CanRead = true, CanWrite = canWrite
            });
            await db.SaveChangesAsync();
        });

    [Fact]
    public async Task Remove_TakesThemOut_AndListsWhoRemovedThem()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 4);

        var response = await RemoveAsync(client, albumId, ids[2], ids[1], Guid.NewGuid());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var result = await response.Content.ReadFromJsonAsync<RemoveResult>();
        Assert.Equal([ids[1], ids[2]], result!.Removed);
        Assert.Equal(1, result.Skipped);
        Assert.Equal([ids[0], ids[3]], await AlbumContentsAsync(client, albumId));

        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        Assert.Equal([ids[1], ids[2]], removed!.Select(r => r.Asset.Id));
        Assert.All(removed!, r =>
        {
            Assert.Equal(owner.Id, r.RemovedByUserId);
            Assert.Equal(owner.Username, r.RemovedByUsername);
            Assert.True(DateTime.UtcNow - r.RemovedAt < TimeSpan.FromMinutes(1));
        });
    }

    [Fact]
    public async Task SingleDelete_IsRecordedToo()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 2);

        var response = await client.DeleteAsync($"/api/albums/{albumId}/assets/{ids[1]}");

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        Assert.Equal([ids[1]], removed!.Select(r => r.Asset.Id));
    }

    [Fact]
    public async Task Restore_PutsThemBackInPlace_AndTheCoverBack()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 4);
        await RemoveAsync(client, albumId, ids[0], ids[2]);

        var album = await client.GetFromJsonAsync<AlbumDto>($"/api/albums/{albumId}");
        Assert.DoesNotContain(ids[0].ToString(), album!.CoverThumbnailUrl ?? "");

        var response = await RestoreAsync(client, albumId, ids[0], ids[2], ids[3]);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var result = await response.Content.ReadFromJsonAsync<RestoreResult>();
        Assert.Equal([ids[0], ids[2]], result!.Restored);
        Assert.Equal(1, result.Skipped);
        Assert.Equal(ids, await AlbumContentsAsync(client, albumId));

        var rows = await WithDbContextAsync(db => db.AlbumAssets.AsNoTracking()
            .Where(aa => aa.AlbumId == albumId).OrderBy(aa => aa.Order).ToListAsync());
        Assert.Equal(FirstAddedAt.AddMinutes(2), rows.Single(aa => aa.AssetId == ids[2]).AddedAt);
        var coverId = await WithDbContextAsync(db => db.Albums.Where(a => a.Id == albumId)
            .Select(a => a.CoverAssetId).SingleAsync());
        Assert.Equal(ids[0], coverId);

        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        Assert.Empty(removed!);
    }

    [Fact]
    public async Task Restore_KeepsACoverChosenMeanwhile()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 3);
        await RemoveAsync(client, albumId, ids[0]);
        var cover = await client.PutAsJsonAsync($"/api/albums/{albumId}/cover", new { assetId = ids[2] });
        cover.EnsureSuccessStatusCode();

        await RestoreAsync(client, albumId, ids[0]);

        var coverId = await WithDbContextAsync(db => db.Albums.Where(a => a.Id == albumId)
            .Select(a => a.CoverAssetId).SingleAsync());
        Assert.Equal(ids[2], coverId);
    }

    [Fact]
    public async Task AddingItBackByHand_ClearsTheRecord()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 3);
        await RemoveAsync(client, albumId, ids[1], ids[2]);

        var add = await client.PostAsJsonAsync($"/api/albums/{albumId}/assets/batch", new { assetIds = new[] { ids[1] } });
        add.EnsureSuccessStatusCode();
        var addOne = await client.PostAsJsonAsync($"/api/albums/{albumId}/assets", new { assetId = ids[2] });
        addOne.EnsureSuccessStatusCode();

        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        Assert.Empty(removed!);
    }

    [Fact]
    public async Task RemovingAgain_RefreshesTheRecord()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (editor, editorClient) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 2);
        await GrantAsync(albumId, owner.Id, editor.Id, canWrite: true);

        await RemoveAsync(client, albumId, ids[1]);
        await RestoreAsync(client, albumId, ids[1]);
        await RemoveAsync(editorClient, albumId, ids[1]);

        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        var item = Assert.Single(removed!);
        Assert.Equal(editor.Username, item.RemovedByUsername);
    }

    [Fact]
    public async Task OnlyEditors_SeeRemoveOrRestore()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (reader, readerClient) = await CreateAuthenticatedUserAsync();
        var (editor, editorClient) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 3);
        await GrantAsync(albumId, owner.Id, reader.Id, canWrite: false);
        await GrantAsync(albumId, owner.Id, editor.Id, canWrite: true);

        Assert.Equal(HttpStatusCode.Forbidden, (await RemoveAsync(readerClient, albumId, ids[0])).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await RemoveAsync(editorClient, albumId, ids[0])).StatusCode);

        Assert.Equal(HttpStatusCode.Forbidden,
            (await readerClient.GetAsync($"/api/albums/{albumId}/assets/removed")).StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, (await RestoreAsync(readerClient, albumId, ids[0])).StatusCode);

        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        Assert.Equal(editor.Id, Assert.Single(removed!).RemovedByUserId);
        Assert.Equal(HttpStatusCode.OK, (await RestoreAsync(editorClient, albumId, ids[0])).StatusCode);
        Assert.Equal(ids, await AlbumContentsAsync(client, albumId));
    }

    [Fact]
    public async Task ExpiredRemovals_AreNeitherListedNorRestored()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 3);
        await RemoveAsync(client, albumId, ids[0], ids[1]);
        await WithDbContextAsync(async db =>
        {
            var old = await db.AlbumAssetRemovals.SingleAsync(r => r.AlbumId == albumId && r.AssetId == ids[0]);
            old.RemovedAt = DateTime.UtcNow - AlbumsEndpoint.RemovalRetention - TimeSpan.FromHours(1);
            await db.SaveChangesAsync();
        });

        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        Assert.Equal([ids[1]], removed!.Select(r => r.Asset.Id));

        var result = await (await RestoreAsync(client, albumId, ids[0])).Content.ReadFromJsonAsync<RestoreResult>();
        Assert.Empty(result!.Restored);

        // The next removal purges what expired.
        await RemoveAsync(client, albumId, ids[2]);
        var stored = await WithDbContextAsync(db => db.AlbumAssetRemovals
            .Where(r => r.AlbumId == albumId).Select(r => r.AssetId).ToListAsync());
        Assert.Equal(new[] { ids[1], ids[2] }.Order(), stored.Order());
    }

    [Fact]
    public async Task TrashedPhotos_AreLeftOut()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 2);
        await RemoveAsync(client, albumId, ids[0], ids[1]);
        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.SingleAsync(a => a.Id == ids[0]);
            asset.DeletedAt = DateTime.UtcNow;
            await db.SaveChangesAsync();
        });

        var removed = await client.GetFromJsonAsync<List<RemovedItem>>($"/api/albums/{albumId}/assets/removed");
        Assert.Equal([ids[1]], removed!.Select(r => r.Asset.Id));

        var result = await (await RestoreAsync(client, albumId, ids[0], ids[1])).Content.ReadFromJsonAsync<RestoreResult>();
        Assert.Equal([ids[1]], result!.Restored);
    }

    [Fact]
    public async Task EmptyRequests_AreRejected()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, _) = await CreateAlbumWithPhotosAsync(owner, 1);

        Assert.Equal(HttpStatusCode.BadRequest, (await RemoveAsync(client, albumId)).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await RestoreAsync(client, albumId)).StatusCode);
    }

    [Fact]
    public async Task DeletingTheAlbum_DropsItsRecords()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var (albumId, ids) = await CreateAlbumWithPhotosAsync(owner, 1);
        await RemoveAsync(client, albumId, ids[0]);

        var delete = await client.DeleteAsync($"/api/albums/{albumId}");

        Assert.Equal(HttpStatusCode.NoContent, delete.StatusCode);
        Assert.False(await WithDbContextAsync(db => db.AlbumAssetRemovals.AnyAsync(r => r.AlbumId == albumId)));
    }
}
