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

    private sealed record StorageLibraryUsage(
        Guid Id,
        string Name,
        int Photos,
        int Videos,
        long PhotoBytes,
        long VideoBytes);

    private sealed record StorageInfo(
        long UsedBytes,
        long? QuotaBytes,
        int Photos,
        int Videos,
        long PhotoBytes,
        long VideoBytes,
        int PersonalPhotos,
        int PersonalVideos,
        long PersonalPhotoBytes,
        long PersonalVideoBytes,
        List<StorageLibraryUsage> Libraries);

    private Task AddAssetAsync(
        Guid ownerId,
        long size,
        bool deleted = false,
        AssetType type = AssetType.Image,
        Guid? externalLibraryId = null)
    {
        return WithDbContextAsync(async db =>
        {
            db.Assets.Add(new Asset
            {
                FileName = Guid.NewGuid().ToString("N") + (type == AssetType.Video ? ".mp4" : ".jpg"),
                FullPath = $"/assets/users/{ownerId}/{Guid.NewGuid():N}.jpg",
                FileSize = size,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = type,
                Extension = type == AssetType.Video ? "mp4" : "jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = ownerId,
                ExternalLibraryId = externalLibraryId,
                DeletedAt = deleted ? DateTime.UtcNow : null
            });
            await db.SaveChangesAsync();
        });
    }

    private Task<Guid> AddExternalLibraryAsync(Guid ownerId, string name)
    {
        return WithDbContextAsync(async db =>
        {
            var lib = new ExternalLibrary
            {
                Name = name,
                Path = "/data/" + Guid.NewGuid().ToString("N"),
                OwnerId = ownerId
            };
            db.ExternalLibraries.Add(lib);
            await db.SaveChangesAsync();
            return lib.Id;
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

    [Fact]
    public async Task ExternalLibraryAssets_AppearInLibrariesBreakdown_NotInPersonal()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var libId = await AddExternalLibraryAsync(alice.Id, "Family NAS");

        // Personal: 1 photo (1000) + 1 video (5000)
        await AddAssetAsync(alice.Id, size: 1_000, type: AssetType.Image);
        await AddAssetAsync(alice.Id, size: 5_000, type: AssetType.Video);
        // External library (same owner): 2 photos (3000+2000) + 1 video (10_000)
        await AddAssetAsync(alice.Id, size: 3_000, type: AssetType.Image, externalLibraryId: libId);
        await AddAssetAsync(alice.Id, size: 2_000, type: AssetType.Image, externalLibraryId: libId);
        await AddAssetAsync(alice.Id, size: 10_000, type: AssetType.Video, externalLibraryId: libId);

        var info = await client.GetFromJsonAsync<StorageInfo>("/api/users/me/storage");

        Assert.NotNull(info);
        // Totals include everything the user owns
        Assert.Equal(21_000L, info!.UsedBytes);
        Assert.Equal(3, info.Photos);
        Assert.Equal(2, info.Videos);
        // Personal subset only counts assets without ExternalLibraryId
        Assert.Equal(1, info.PersonalPhotos);
        Assert.Equal(1, info.PersonalVideos);
        Assert.Equal(1_000L, info.PersonalPhotoBytes);
        Assert.Equal(5_000L, info.PersonalVideoBytes);
        // Library breakdown is present even for the owner (not just members)
        var lib = Assert.Single(info.Libraries);
        Assert.Equal(libId, lib.Id);
        Assert.Equal("Family NAS", lib.Name);
        Assert.Equal(2, lib.Photos);
        Assert.Equal(1, lib.Videos);
        Assert.Equal(5_000L, lib.PhotoBytes);
        Assert.Equal(10_000L, lib.VideoBytes);
    }

    [Fact]
    public async Task UserWithoutLibraries_HasEmptyLibrariesList()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        await AddAssetAsync(alice.Id, size: 1_000, type: AssetType.Image);

        var info = await client.GetFromJsonAsync<StorageInfo>("/api/users/me/storage");

        Assert.NotNull(info);
        Assert.Empty(info!.Libraries);
        Assert.Equal(info.Photos, info.PersonalPhotos);
        Assert.Equal(info.PhotoBytes, info.PersonalPhotoBytes);
    }
}
