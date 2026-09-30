using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Geo;

/// <summary>
/// The viewer's capsule shows "city, country" from the asset detail. The place
/// is the one resolved at index time (AssetExif.PlaceId), so the endpoint only
/// has to join it — never geocode on the request path.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class AssetDetailPlaceTests : IntegrationTestBase
{
    public AssetDetailPlaceTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record ExifBody(string? PlaceName, string? PlaceCountryCode);
    private sealed record DetailBody(Guid Id, ExifBody? Exif);

    private async Task<Guid> CreateAssetAsync(TestUser user, bool withPlace)
    {
        return await WithDbContextAsync(async db =>
        {
            var name = $"{Guid.NewGuid():N}.jpg";
            var asset = new Asset
            {
                FileName = name,
                FullPath = $"/assets/users/{user.Username}/{name}",
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = new DateTime(2024, 6, 12, 18, 42, 0),
                FileModifiedAt = new DateTime(2024, 6, 12, 18, 42, 0),
                CapturedAt = new DateTime(2024, 6, 12, 18, 42, 0),
                OwnerId = user.Id,
            };
            db.Assets.Add(asset);
            var exif = new AssetExif { Asset = asset, Latitude = 41.98, Longitude = 2.82 };
            if (withPlace)
            {
                exif.Place = new Place
                {
                    GeonameId = 3121456,
                    Name = "Girona",
                    CountryCode = "ES",
                    Latitude = 41.9831,
                    Longitude = 2.8249,
                };
                exif.GeocodedAt = DateTime.UtcNow;
            }
            db.AssetExifs.Add(exif);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    [Fact]
    public async Task Detail_CarriesTheResolvedPlace()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetAsync(user, withPlace: true);

        var detail = await client.GetFromJsonAsync<DetailBody>($"/api/assets/{assetId}");

        Assert.NotNull(detail?.Exif);
        Assert.Equal("Girona", detail!.Exif!.PlaceName);
        Assert.Equal("ES", detail.Exif.PlaceCountryCode);
    }

    [Fact]
    public async Task Detail_WithoutAResolvedPlace_LeavesItNull()
    {
        // GPS but not geocoded yet (or no dataset): the endpoint doesn't guess.
        var (user, client) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetAsync(user, withPlace: false);

        var detail = await client.GetFromJsonAsync<DetailBody>($"/api/assets/{assetId}");

        Assert.NotNull(detail?.Exif);
        Assert.Null(detail!.Exif!.PlaceName);
        Assert.Null(detail.Exif.PlaceCountryCode);
    }
}
