using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Geo;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Geo;

/// <summary>
/// Inferring a photo's location from the ones around it.
///
/// The tests that matter most here are the ones where it must REFUSE: this
/// writes coordinates that no camera measured, so every case where the honest
/// answer is "unknown" has to stay unknown.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class LocationInterpolationTests : IntegrationTestBase
{
    public LocationInterpolationTests(PhotonneApiFactory factory) : base(factory) { }

    private static readonly DateTime Noon = new(2024, 6, 1, 12, 0, 0);

    // Girona and Barcelona: ~85 km apart, which is the "you were driving" case.
    private const double GironaLat = 41.98, GironaLon = 2.82;
    private const double BarcelonaLat = 41.39, BarcelonaLon = 2.17;

    private async Task<InterpolationResult> RunAsync()
    {
        using var scope = Factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        return await new LocationInterpolationRunner(db).RunAsync(CancellationToken.None);
    }

    /// <summary>One photo at [minutesFromNoon], with or without a real fix.</summary>
    private async Task<Guid> PhotoAsync(
        TestUser owner,
        int minutesFromNoon,
        double? lat = null,
        double? lon = null,
        LocationSource source = LocationSource.None) =>
        await WithDbContextAsync(async db =>
        {
            var when = Noon.AddMinutes(minutesFromNoon);
            var name = $"{Guid.NewGuid():N}.jpg";
            var asset = new Asset
            {
                FileName = name,
                FullPath = $"/assets/users/{owner.Username}/{name}",
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = when,
                FileModifiedAt = when,
                CapturedAt = when,
                OwnerId = owner.Id,
            };
            db.Assets.Add(asset);
            db.AssetExifs.Add(new AssetExif
            {
                Asset = asset,
                Latitude = lat,
                Longitude = lon,
                LocationSource = source,
            });
            await db.SaveChangesAsync();
            return asset.Id;
        });

    private Task<Guid> GpsPhotoAsync(TestUser owner, int minutesFromNoon, double lat, double lon) =>
        PhotoAsync(owner, minutesFromNoon, lat, lon, LocationSource.Exif);

    private async Task<AssetExif> ExifOfAsync(Guid assetId) =>
        await WithDbContextAsync(async db => await db.AssetExifs.SingleAsync(e => e.AssetId == assetId));

    [Fact]
    public async Task FillsAPhotoBracketedByTwoAgreeingFixes()
    {
        // Phone shot, camera shot, phone shot — all within a few minutes and a
        // few hundred metres. The camera shot was obviously taken right there.
        var user = await CreateUserAsync();
        await GpsPhotoAsync(user, -10, GironaLat, GironaLon);
        var camera = await PhotoAsync(user, 0);
        await GpsPhotoAsync(user, +10, GironaLat + 0.002, GironaLon + 0.002);

        var result = await RunAsync();

        Assert.Equal(1, result.Filled);
        var exif = await ExifOfAsync(camera);
        Assert.Equal(LocationSource.Interpolated, exif.LocationSource);
        Assert.NotNull(exif.Latitude);
        Assert.Equal(GironaLat, exif.Latitude!.Value, precision: 2);
    }

    [Fact]
    public async Task RefusesWhenTheBracketingFixesDisagree()
    {
        // Girona at noon, Barcelona two hours later: the photo in between was
        // taken on the motorway. "Somewhere along the way" is not a location,
        // and guessing either end would put it 85 km from where it was.
        var user = await CreateUserAsync();
        await GpsPhotoAsync(user, -60, GironaLat, GironaLon);
        var enRoute = await PhotoAsync(user, 0);
        await GpsPhotoAsync(user, +60, BarcelonaLat, BarcelonaLon);

        var result = await RunAsync();

        Assert.Equal(0, result.Filled);
        var exif = await ExifOfAsync(enRoute);
        Assert.Null(exif.Latitude);
        Assert.Equal(LocationSource.None, exif.LocationSource);
    }

    [Fact]
    public async Task TrustsASingleFixOnlyOverAShortHop()
    {
        var user = await CreateUserAsync();
        // Last photo of the day has only a "before" anchor, 5 minutes back: in
        // five minutes you cannot have left town.
        await GpsPhotoAsync(user, -5, GironaLat, GironaLon);
        var justAfter = await PhotoAsync(user, 0);

        var result = await RunAsync();

        Assert.Equal(1, result.Filled);
        Assert.Equal(LocationSource.Interpolated, (await ExifOfAsync(justAfter)).LocationSource);
    }

    [Fact]
    public async Task RefusesASingleDistantFix()
    {
        var user = await CreateUserAsync();
        // 90 minutes after the only anchor, with nothing after it. Could be the
        // same square; could be another province. Unknown is the honest answer.
        await GpsPhotoAsync(user, -90, GironaLat, GironaLon);
        var later = await PhotoAsync(user, 0);

        var result = await RunAsync();

        Assert.Equal(0, result.Filled);
        Assert.Null((await ExifOfAsync(later)).Latitude);
    }

    [Fact]
    public async Task NeverOverwritesARealFix()
    {
        var user = await CreateUserAsync();
        await GpsPhotoAsync(user, -5, GironaLat, GironaLon);
        // Its own GPS says Barcelona. Wrong-looking next to its neighbours, but
        // it's a measurement and we are not in the business of correcting the
        // camera.
        var measured = await GpsPhotoAsync(user, 0, BarcelonaLat, BarcelonaLon);
        await GpsPhotoAsync(user, +5, GironaLat, GironaLon);

        await RunAsync();

        var exif = await ExifOfAsync(measured);
        Assert.Equal(LocationSource.Exif, exif.LocationSource);
        Assert.Equal(BarcelonaLat, exif.Latitude!.Value, precision: 2);
    }

    [Fact]
    public async Task NeverAnchorsOnAnInferredValue()
    {
        // A chain would drift: each hop is allowed to be 25 km off, so five hops
        // put a photo in the next province with nobody the wiser. Only measured
        // fixes may anchor.
        var user = await CreateUserAsync();
        await GpsPhotoAsync(user, 0, GironaLat, GironaLon);
        var first = await PhotoAsync(user, 5);
        // Far past any anchor's reach, but 5 minutes from `first` — reachable
        // only if `first`'s inferred value were allowed to anchor.
        var chained = await PhotoAsync(user, 10);

        await RunAsync();
        // A second pass is where a chain would show up: `first` now has
        // coordinates, so a careless implementation would use them.
        await RunAsync();

        Assert.Equal(LocationSource.Interpolated, (await ExifOfAsync(first)).LocationSource);
        Assert.Equal(LocationSource.Interpolated, (await ExifOfAsync(chained)).LocationSource);

        // Both must trace back to the ONE real fix, not to each other.
        var chainedExif = await ExifOfAsync(chained);
        Assert.Equal(GironaLat, chainedExif.Latitude!.Value, precision: 2);
    }

    [Fact]
    public async Task NeverBorrowsAnotherUsersLocation()
    {
        var alice = await CreateUserAsync();
        var bob = await CreateUserAsync();
        // Alice was in Girona at noon. That says nothing whatsoever about where
        // Bob was.
        await GpsPhotoAsync(alice, 0, GironaLat, GironaLon);
        var bobsPhoto = await PhotoAsync(bob, 2);

        var result = await RunAsync();

        Assert.Equal(0, result.Filled);
        Assert.Null((await ExifOfAsync(bobsPhoto)).Latitude);
    }

    [Fact]
    public async Task IsIdempotentAndReleasesTheGeocoder()
    {
        var user = await CreateUserAsync();
        await GpsPhotoAsync(user, -5, GironaLat, GironaLon);
        var camera = await PhotoAsync(user, 0);
        await GpsPhotoAsync(user, +5, GironaLat, GironaLon);

        var first = await RunAsync();
        var second = await RunAsync();

        Assert.Equal(1, first.Filled);
        // A filled photo has coordinates, so it isn't a candidate any more.
        Assert.Equal(0, second.Filled);

        // GeocodedAt left null on purpose: the geocode pass runs next and has to
        // see this photo as new work, or an inferred location never gets a name.
        Assert.Null((await ExifOfAsync(camera)).GeocodedAt);
    }
}
