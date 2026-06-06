using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.UploadAssets;

/// <summary>
/// End-to-end checks of POST /api/assets/check-checksums: the bulk dedup
/// lookup the mobile backup client uses to verify pending items in one call.
/// </summary>
public sealed class CheckChecksumsEndpointTests : IntegrationTestBase
{
    public CheckChecksumsEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record CheckChecksumsResponse(Dictionary<string, Guid> Existing);

    private async Task<Asset> SeedAssetAsync(Guid ownerId, string checksum, DateTime? deletedAt = null)
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
            return asset;
        });
    }

    private static string NewChecksum() => Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N");

    [Fact]
    public async Task ReturnsMatchingChecksums_AndOmitsUnknownOnes()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var known1 = NewChecksum();
        var known2 = NewChecksum();
        var unknown = NewChecksum();
        var asset1 = await SeedAssetAsync(user.Id, known1);
        var asset2 = await SeedAssetAsync(user.Id, known2);

        var response = await client.PostAsJsonAsync("/api/assets/check-checksums", new
        {
            Checksums = new[] { known1, known2, unknown }
        });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<CheckChecksumsResponse>();
        Assert.NotNull(body);
        Assert.Equal(2, body!.Existing.Count);
        Assert.Equal(asset1.Id, body.Existing[known1]);
        Assert.Equal(asset2.Id, body.Existing[known2]);
        Assert.False(body.Existing.ContainsKey(unknown));
    }

    [Fact]
    public async Task DoesNotMatchOtherUsersAssets()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (_, otherClient) = await CreateAuthenticatedUserAsync();
        var checksum = NewChecksum();
        await SeedAssetAsync(owner.Id, checksum);

        var response = await otherClient.PostAsJsonAsync("/api/assets/check-checksums", new
        {
            Checksums = new[] { checksum }
        });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<CheckChecksumsResponse>();
        Assert.NotNull(body);
        Assert.Empty(body!.Existing);
    }

    [Fact]
    public async Task DoesNotMatchSoftDeletedAssets()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var checksum = NewChecksum();
        await SeedAssetAsync(user.Id, checksum, deletedAt: DateTime.UtcNow);

        var response = await client.PostAsJsonAsync("/api/assets/check-checksums", new
        {
            Checksums = new[] { checksum }
        });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<CheckChecksumsResponse>();
        Assert.NotNull(body);
        Assert.Empty(body!.Existing);
    }

    [Fact]
    public async Task NormalizesCasingAndWhitespace()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var checksum = NewChecksum();
        var asset = await SeedAssetAsync(user.Id, checksum);

        var response = await client.PostAsJsonAsync("/api/assets/check-checksums", new
        {
            Checksums = new[] { "  " + checksum.ToUpperInvariant() + "  " }
        });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<CheckChecksumsResponse>();
        Assert.NotNull(body);
        Assert.Equal(asset.Id, body!.Existing[checksum]);
    }

    [Fact]
    public async Task EmptyList_ReturnsEmptyMap()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var response = await client.PostAsJsonAsync("/api/assets/check-checksums", new
        {
            Checksums = Array.Empty<string>()
        });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<CheckChecksumsResponse>();
        Assert.NotNull(body);
        Assert.Empty(body!.Existing);
    }

    [Fact]
    public async Task OverLimit_ReturnsBadRequest()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var checksums = Enumerable.Range(0, 1001).Select(_ => NewChecksum()).ToArray();

        var response = await client.PostAsJsonAsync("/api/assets/check-checksums", new { Checksums = checksums });
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task RequiresAuthentication()
    {
        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/assets/check-checksums", new
        {
            Checksums = new[] { NewChecksum() }
        });
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }
}
