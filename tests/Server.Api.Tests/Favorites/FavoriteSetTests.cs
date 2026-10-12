using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Favorites;

/// <summary>
/// PUT /api/assets/{id}/favorite and PUT /api/assets/favorite set the heart to
/// a value: repeating the call keeps it, unlike the POST toggle.
/// </summary>
public sealed class FavoriteSetTests : IntegrationTestBase
{
    public FavoriteSetTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record FavoriteBody(bool IsFavorite);
    private sealed record BatchBody(List<Guid> AssetIds, bool IsFavorite);

    private async Task<Guid> CreateAssetAsync(TestUser owner, bool isFavorite = false)
    {
        return await WithDbContextAsync(async db =>
        {
            var name = $"{Guid.NewGuid():N}.jpg";
            var asset = new Asset
            {
                FileName = name,
                FullPath = $"/assets/users/{owner.Username}/{name}",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = owner.Id,
                IsFavorite = isFavorite
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    private Task<bool> IsFavoriteAsync(Guid assetId)
        => WithDbContextAsync(db => db.Assets.AsNoTracking()
            .Where(a => a.Id == assetId).Select(a => a.IsFavorite).FirstAsync());

    [Fact]
    public async Task SetOne_IsIdempotent()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetAsync(alice);

        for (var i = 0; i < 2; i++)
        {
            var response = await client.PutAsJsonAsync($"/api/assets/{assetId}/favorite", new FavoriteBody(true));
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
            var body = await response.Content.ReadFromJsonAsync<FavoriteBody>();
            Assert.True(body!.IsFavorite);
        }
        Assert.True(await IsFavoriteAsync(assetId));

        var cleared = await client.PutAsJsonAsync($"/api/assets/{assetId}/favorite", new FavoriteBody(false));
        Assert.False((await cleared.Content.ReadFromJsonAsync<FavoriteBody>())!.IsFavorite);
        Assert.False(await IsFavoriteAsync(assetId));
    }

    [Fact]
    public async Task SetOne_ReturnsNotFound_ForAnAssetTheCallerCannotRead()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetAsync(alice);

        var response = await bobClient.PutAsJsonAsync($"/api/assets/{assetId}/favorite", new FavoriteBody(true));

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.False(await IsFavoriteAsync(assetId));
    }

    [Fact]
    public async Task SetMany_SetsTheReadableOnes_AndLeavesTheRestOut()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();
        var already = await CreateAssetAsync(alice, isFavorite: true);
        var notYet = await CreateAssetAsync(alice);
        var bobs = await CreateAssetAsync(bob);
        var missing = Guid.NewGuid();

        var response = await aliceClient.PutAsJsonAsync("/api/assets/favorite",
            new BatchBody([already, notYet, bobs, missing], true));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<BatchBody>();
        Assert.True(body!.IsFavorite);
        Assert.Equal(new[] { already, notYet }.OrderBy(x => x), body.AssetIds.OrderBy(x => x));
        Assert.True(await IsFavoriteAsync(already));
        Assert.True(await IsFavoriteAsync(notYet));
        Assert.False(await IsFavoriteAsync(bobs));
    }

    [Fact]
    public async Task Toggle_ReturnsNotFound_ForAnAssetTheCallerCannotRead()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetAsync(alice);

        var response = await bobClient.PostAsync($"/api/assets/{assetId}/favorite", null);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.False(await IsFavoriteAsync(assetId));
    }

    [Fact]
    public async Task SetMany_RejectsAnEmptySelection()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var response = await client.PutAsJsonAsync("/api/assets/favorite", new BatchBody([], true));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }
}
