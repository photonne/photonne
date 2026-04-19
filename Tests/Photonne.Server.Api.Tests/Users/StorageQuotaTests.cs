using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Users;

/// <summary>
/// /api/users/me/storage sums FileSize of the caller's non-deleted assets.
/// Bugs here are easy to introduce (soft-deleted rows, cross-user leakage)
/// and users notice immediately when the quota display is wrong.
/// </summary>
public sealed class StorageQuotaTests : IntegrationTestBase
{
    public StorageQuotaTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record StorageInfo(long UsedBytes, long? QuotaBytes);

    private Task AddAssetAsync(Guid ownerId, long size, bool deleted = false)
    {
        return WithDbContextAsync(async db =>
        {
            db.Assets.Add(new Asset
            {
                FileName = Guid.NewGuid().ToString("N") + ".jpg",
                FullPath = $"/assets/users/{ownerId}/{Guid.NewGuid():N}.jpg",
                FileSize = size,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = ownerId,
                DeletedAt = deleted ? DateTime.UtcNow : null
            });
            await db.SaveChangesAsync();
        });
    }

    [Fact]
    public async Task EmptyLibrary_ReportsZeroUsed()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var info = await client.GetFromJsonAsync<StorageInfo>("/api/users/me/storage");

        Assert.NotNull(info);
        Assert.Equal(0L, info!.UsedBytes);
    }

    [Fact]
    public async Task UsedBytes_SumsOwnedNonDeletedAssets()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        await AddAssetAsync(alice.Id, size: 1_000);
        await AddAssetAsync(alice.Id, size: 2_500);
        await AddAssetAsync(alice.Id, size: 500);

        var info = await client.GetFromJsonAsync<StorageInfo>("/api/users/me/storage");

        Assert.Equal(4_000L, info!.UsedBytes);
    }

    [Fact]
    public async Task TrashedAssets_AreExcluded_FromUsedBytes()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        await AddAssetAsync(alice.Id, size: 1_000);
        await AddAssetAsync(alice.Id, size: 9_999, deleted: true);

        var info = await client.GetFromJsonAsync<StorageInfo>("/api/users/me/storage");

        Assert.Equal(1_000L, info!.UsedBytes);
    }

    [Fact]
    public async Task AnotherUsersAssets_DontCount()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();

        await AddAssetAsync(alice.Id, size: 100);
        await AddAssetAsync(bob.Id, size: 10_000);

        var info = await aliceClient.GetFromJsonAsync<StorageInfo>("/api/users/me/storage");

        Assert.Equal(100L, info!.UsedBytes);
    }

    [Fact]
    public async Task QuotaBytes_ReflectsUserField()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();

        await WithDbContextAsync(async db =>
        {
            var u = db.Users.First(x => x.Id == alice.Id);
            u.StorageQuotaBytes = 5L * 1024 * 1024 * 1024;
            await db.SaveChangesAsync();
        });

        var info = await client.GetFromJsonAsync<StorageInfo>("/api/users/me/storage");

        Assert.Equal(5L * 1024 * 1024 * 1024, info!.QuotaBytes);
    }
}
