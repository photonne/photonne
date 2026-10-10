using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Geo;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Geo;

/// <summary>
/// Trip detection against a real database: the algorithm itself is covered by
/// TripClusteringTests, so what matters here is the persistence — that a second
/// nightly pass refines the same rows instead of duplicating them, and that one
/// user's trips are built only from their own photos.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class TripDetectionTests : IntegrationTestBase
{
    public TripDetectionTests(PhotonneApiFactory factory) : base(factory) { }

    private static readonly DateTime Today = new(2024, 6, 1);

    private const double BarcelonaLat = 41.39, BarcelonaLon = 2.17;
    private const double RomeLat = 41.90, RomeLon = 12.50;

    private async Task<TripDetectionResult> RunAsync(TestUser user)
    {
        using var scope = Factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        return await new TripDetectionService(db).RunForUserAsync(user.Id, Today, CancellationToken.None);
    }

    private async Task PhotosAsync(TestUser owner, DateTime day, double lat, double lon, int count = 8)
    {
        await WithDbContextAsync(async db =>
        {
            for (var i = 0; i < count; i++)
            {
                var when = day.Date.AddHours(10).AddHours(i);
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
                    LocationSource = LocationSource.Exif,
                });
            }
            await db.SaveChangesAsync();
            return 0;
        });
    }

    /// <summary>Enough days at home for FindHome to be confident.</summary>
    private async Task LiveInBarcelonaAsync(TestUser user)
    {
        for (var d = 0; d < 70; d++)
            await PhotosAsync(user, Today.AddDays(-200 + d), BarcelonaLat, BarcelonaLon, count: 2);
    }

    private Task<List<Trip>> TripsOfAsync(TestUser user) =>
        WithDbContextAsync(async db => await db.Trips
            .Where(t => t.OwnerId == user.Id)
            .OrderBy(t => t.WindowStart)
            .ToListAsync());

    [Fact]
    public async Task DetectsATripAwayFromHome()
    {
        var user = await CreateUserAsync();
        await LiveInBarcelonaAsync(user);
        await PhotosAsync(user, Today.AddDays(-30), RomeLat, RomeLon);
        await PhotosAsync(user, Today.AddDays(-29), RomeLat, RomeLon);

        var result = await RunAsync(user);

        Assert.True(result.HomeFound);
        Assert.Equal(1, result.Created);

        var trip = Assert.Single(await TripsOfAsync(user));
        Assert.Equal(16, trip.AssetCount);
    }

    [Fact]
    public async Task WithoutAHome_FindsNoTrips()
    {
        // Barely any photos, so no cell is lived-in enough. Guessing a home here
        // would turn this user's whole library into trips.
        var user = await CreateUserAsync();
        await PhotosAsync(user, Today.AddDays(-30), RomeLat, RomeLon);
        await PhotosAsync(user, Today.AddDays(-29), RomeLat, RomeLon);

        var result = await RunAsync(user);

        Assert.False(result.HomeFound);
        Assert.Empty(await TripsOfAsync(user));
    }

    [Fact]
    public async Task RunningTwice_RefinesTheSameTripInsteadOfDuplicatingIt()
    {
        var user = await CreateUserAsync();
        await LiveInBarcelonaAsync(user);
        await PhotosAsync(user, Today.AddDays(-30), RomeLat, RomeLon);
        await PhotosAsync(user, Today.AddDays(-29), RomeLat, RomeLon);

        await RunAsync(user);
        var first = Assert.Single(await TripsOfAsync(user));

        // The trip grows by a day, as if more photos had just been imported.
        await PhotosAsync(user, Today.AddDays(-28), RomeLat, RomeLon);
        var second = await RunAsync(user);

        Assert.Equal(0, second.Created);
        Assert.Equal(1, second.Updated);

        var trip = Assert.Single(await TripsOfAsync(user));
        Assert.Equal(first.Id, trip.Id);
        Assert.Equal(24, trip.AssetCount);
    }

    [Fact]
    public async Task ATripIsBuiltOnlyFromTheOwnersOwnPhotos()
    {
        // B lives in Barcelona and never left; A's Roman holiday is A's, and must
        // not leak into B's trips even though the rows sit in the same table.
        var a = await CreateUserAsync();
        var b = await CreateUserAsync();

        await LiveInBarcelonaAsync(b);
        await PhotosAsync(a, Today.AddDays(-30), RomeLat, RomeLon);
        await PhotosAsync(a, Today.AddDays(-29), RomeLat, RomeLon);

        await RunAsync(b);

        Assert.Empty(await TripsOfAsync(b));
    }

    [Fact]
    public async Task ArchivedPhotosDoNotMakeATrip()
    {
        var user = await CreateUserAsync();
        await LiveInBarcelonaAsync(user);
        await PhotosAsync(user, Today.AddDays(-30), RomeLat, RomeLon);
        await PhotosAsync(user, Today.AddDays(-29), RomeLat, RomeLon);

        await WithDbContextAsync(async db =>
        {
            var archived = await db.Assets
                .Where(a => a.OwnerId == user.Id && a.CapturedAt >= Today.AddDays(-30))
                .ToListAsync();
            archived.ForEach(a => a.IsArchived = true);
            await db.SaveChangesAsync();
            return 0;
        });

        await RunAsync(user);

        Assert.Empty(await TripsOfAsync(user));
    }
}
