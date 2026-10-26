using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Places;

/// <summary>The search page's places row: where my photos were taken, most first.</summary>
public sealed class PlacesEndpointTests : IntegrationTestBase
{
    public PlacesEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record PlaceItem(Guid Id, string Name, string CountryCode, int AssetCount, Guid CoverAssetId);

    private Task<Guid> CreatePhotoAsync(TestUser owner, DateTime capturedAt, Place? place, bool archived = false) =>
        WithDbContextAsync(async db =>
        {
            var name = $"{Guid.NewGuid():N}.jpg";
            var asset = new Asset
            {
                FileName = name,
                FullPath = $"/assets/users/{owner.Username}/{name}",
                FileSize = 1,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                IsArchived = archived
            };
            db.Assets.Add(asset);
            if (place != null)
            {
                if (db.Entry(place).State == Microsoft.EntityFrameworkCore.EntityState.Detached)
                    db.Places.Attach(place);
                db.AssetExifs.Add(new AssetExif { Asset = asset, PlaceId = place.Id });
            }
            await db.SaveChangesAsync();
            return asset.Id;
        });

    [Fact]
    public async Task ListsMyPlaces_MostPhotographedFirst_WithTheNewestAsCover()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();
        var (madrid, lisboa) = await WithDbContextAsync(async db =>
        {
            var m = new Place { GeonameId = 3117735, Name = "Madrid", CountryCode = "ES", Latitude = 40.4, Longitude = -3.7 };
            var l = new Place { GeonameId = 2267057, Name = "Lisboa", CountryCode = "PT", Latitude = 38.7, Longitude = -9.1 };
            db.Places.AddRange(m, l);
            await db.SaveChangesAsync();
            return (m, l);
        });

        await CreatePhotoAsync(alice, new DateTime(2025, 1, 1, 0, 0, 0, DateTimeKind.Utc), madrid);
        var newestInMadrid = await CreatePhotoAsync(alice, new DateTime(2025, 6, 1, 0, 0, 0, DateTimeKind.Utc), madrid);
        await CreatePhotoAsync(alice, new DateTime(2024, 1, 1, 0, 0, 0, DateTimeKind.Utc), lisboa);
        await CreatePhotoAsync(alice, new DateTime(2024, 2, 1, 0, 0, 0, DateTimeKind.Utc), lisboa, archived: true);
        await CreatePhotoAsync(alice, new DateTime(2024, 3, 1, 0, 0, 0, DateTimeKind.Utc), place: null);
        await CreatePhotoAsync(bob, new DateTime(2024, 4, 1, 0, 0, 0, DateTimeKind.Utc), lisboa);
        await CreatePhotoAsync(bob, new DateTime(2024, 5, 1, 0, 0, 0, DateTimeKind.Utc), lisboa);

        var places = await client.GetFromJsonAsync<List<PlaceItem>>("/api/places");

        Assert.Equal(new[] { "Madrid", "Lisboa" }, places!.Select(p => p.Name));
        Assert.Equal(new[] { 2, 1 }, places.Select(p => p.AssetCount));
        Assert.Equal(newestInMadrid, places[0].CoverAssetId);
        Assert.Equal("ES", places[0].CountryCode);
    }
}
