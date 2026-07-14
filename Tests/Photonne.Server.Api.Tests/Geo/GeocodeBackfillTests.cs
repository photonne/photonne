using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Geo;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Geo;

/// <summary>
/// The backfill against a real database. It exists for libraries indexed before
/// the dataset shipped, so what matters is that it can be run repeatedly without
/// redoing work — and that it does nothing at all when there's no dataset.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class GeocodeBackfillTests : IntegrationTestBase
{
    public GeocodeBackfillTests(PhotonneApiFactory factory) : base(factory) { }

    private static readonly GeoCity[] Cities =
    [
        new(3128760, "Barcelona", "ES", 41.3888, 2.159, 1_620_343),
        new(3121456, "Girona", "ES", 41.9831, 2.8249, 96_722),
    ];

    /// <summary>
    /// Runs the backfill with an explicit city list. The DI-registered geocoder
    /// points at the image's baked dataset, which isn't there during tests — so
    /// the resolver is assembled by hand rather than resolved.
    /// </summary>
    private async Task<GeocodeBackfillResult> RunBackfillAsync(bool withDataset = true, int? max = null)
    {
        using var scope = Factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var geocoder = withDataset
            ? new ReverseGeocoder(Cities)
            : new ReverseGeocoder("/nonexistent/cities.tsv.gz");
        var runner = new GeocodeBackfillRunner(db, new PlaceResolver(db, geocoder));
        return await runner.RunAsync(max, CancellationToken.None);
    }

    private async Task<Guid> CreateAssetWithGpsAsync(double? lat, double? lon)
    {
        var user = await CreateUserAsync();
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
                FileCreatedAt = new DateTime(2024, 6, 1),
                FileModifiedAt = new DateTime(2024, 6, 1),
                CapturedAt = new DateTime(2024, 6, 1),
                OwnerId = user.Id,
            };
            db.Assets.Add(asset);
            db.AssetExifs.Add(new AssetExif
            {
                Asset = asset,
                Latitude = lat,
                Longitude = lon,
            });
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    [Fact]
    public async Task Backfill_ResolvesAPlaceAndMaterializesItOnce()
    {
        await CreateAssetWithGpsAsync(41.39, 2.16);
        await CreateAssetWithGpsAsync(41.40, 2.15);

        var result = await RunBackfillAsync();

        Assert.Equal(2, result.Processed);
        Assert.Equal(2, result.Matched);
        Assert.Equal(0, result.Pending);

        var places = await WithDbContextAsync(async db => await db.Places.ToListAsync());
        // Both photos are in Barcelona, so there is exactly one Place row: the
        // table holds cities, not sightings.
        var place = Assert.Single(places);
        Assert.Equal("Barcelona", place.Name);
        Assert.Equal("ES", place.CountryCode);

        var exifs = await WithDbContextAsync(async db => await db.AssetExifs.ToListAsync());
        Assert.All(exifs, e =>
        {
            Assert.Equal(place.Id, e.PlaceId);
            Assert.NotNull(e.GeocodedAt);
            Assert.NotNull(e.GeocodeDistanceMeters);
        });
    }

    [Fact]
    public async Task Backfill_IsResumableAndDoesNotRedoWork()
    {
        await CreateAssetWithGpsAsync(41.39, 2.16);
        await CreateAssetWithGpsAsync(41.98, 2.82);

        var first = await RunBackfillAsync();
        var second = await RunBackfillAsync();

        Assert.Equal(2, first.Processed);
        // Second pass finds nothing pending: GeocodedAt is the resume marker, so
        // a nightly run over a caught-up library costs one count query.
        Assert.Equal(0, second.Processed);
        Assert.Equal(0, second.Pending);
    }

    [Fact]
    public async Task Backfill_MarksUnmatchableCoordinatesAsDone()
    {
        // Mid-Atlantic: no city within the cap. The stamp means "we looked", so
        // this photo must leave the pending set rather than be retried nightly
        // for the rest of its life.
        await CreateAssetWithGpsAsync(35.0, -40.0);

        var first = await RunBackfillAsync();
        var second = await RunBackfillAsync();

        Assert.Equal(1, first.Processed);
        Assert.Equal(0, first.Matched);
        Assert.Equal(0, second.Processed);

        var exif = await WithDbContextAsync(async db => await db.AssetExifs.SingleAsync());
        Assert.NotNull(exif.GeocodedAt);
        Assert.Null(exif.PlaceId);
    }

    [Fact]
    public async Task Backfill_SkipsPhotosWithoutGpsAndNullIsland()
    {
        await CreateAssetWithGpsAsync(null, null);
        await CreateAssetWithGpsAsync(0, 0);

        var result = await RunBackfillAsync();

        Assert.Equal(0, result.Processed);
        Assert.Equal(0, result.Pending);
    }

    [Fact]
    public async Task WithoutADataset_BackfillLeavesEverythingPending()
    {
        await CreateAssetWithGpsAsync(41.39, 2.16);

        var result = await RunBackfillAsync(withDataset: false);

        // Nothing is stamped: marking these "looked at, found nothing" would make
        // them invisible to the backfill forever once a dataset finally shipped.
        Assert.Equal(0, result.Processed);
        Assert.Equal(1, result.Pending);

        var exif = await WithDbContextAsync(async db => await db.AssetExifs.SingleAsync());
        Assert.Null(exif.GeocodedAt);
    }

    [Fact]
    public async Task Backfill_HonoursItsBatchLimit()
    {
        await CreateAssetWithGpsAsync(41.39, 2.16);
        await CreateAssetWithGpsAsync(41.40, 2.15);
        await CreateAssetWithGpsAsync(41.98, 2.82);

        var result = await RunBackfillAsync(max: 2);

        Assert.Equal(2, result.Processed);
        Assert.Equal(1, result.Pending);
    }
}
