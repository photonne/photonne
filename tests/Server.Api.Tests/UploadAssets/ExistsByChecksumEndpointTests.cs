using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.UploadAssets;

/// <summary>
/// GET /api/assets/exists/{checksum}: the one-at-a-time dedup lookup the
/// backup falls back to on servers without check-checksums. It answers for
/// the caller's own library only, like check-checksums.
/// </summary>
public sealed class ExistsByChecksumEndpointTests : IntegrationTestBase
{
    public ExistsByChecksumEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record ExistsResponse(Guid AssetId);

    private async Task<Guid> SeedAssetAsync(Guid ownerId, string checksum, DateTime? deletedAt = null)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = $"{Guid.NewGuid():N}.jpg",
                FullPath = $"/assets/users/test/{Guid.NewGuid()}.jpg",
                FileSize = 1024,
                Checksum = checksum,
                Type = AssetType.Image,
                Extension = ".jpg",
                OwnerId = ownerId,
                FileCreatedAt = DateTime.UtcNow,
                DeletedAt = deletedAt,
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    private static string NewChecksum() => Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N");

    [Fact]
    public async Task FindsTheCallersOwnAsset()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var checksum = NewChecksum();
        var assetId = await SeedAssetAsync(user.Id, checksum);

        var response = await client.GetAsync($"/api/assets/exists/{checksum}");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(assetId, (await response.Content.ReadFromJsonAsync<ExistsResponse>())!.AssetId);
    }

    [Fact]
    public async Task DoesNotFindOtherUsersAssets()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (_, otherClient) = await CreateAuthenticatedUserAsync();
        var checksum = NewChecksum();
        await SeedAssetAsync(owner.Id, checksum);

        var response = await otherClient.GetAsync($"/api/assets/exists/{checksum}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task DoesNotFindSoftDeletedAssets()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var checksum = NewChecksum();
        await SeedAssetAsync(user.Id, checksum, deletedAt: DateTime.UtcNow);

        var response = await client.GetAsync($"/api/assets/exists/{checksum}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }
}
