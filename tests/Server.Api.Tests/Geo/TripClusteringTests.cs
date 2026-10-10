using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Tests.Geo;

/// <summary>
/// Trip detection's algorithm, against hand-built timelines. No database: the
/// parts that are easy to get subtly wrong are the gap rules and the home test,
/// and those are pure functions over a list of (time, lat, lon).
/// </summary>
public class TripClusteringTests
{
    private const double BarcelonaLat = 41.39, BarcelonaLon = 2.17;
    private const double RomeLat = 41.90, RomeLon = 12.50;
    private const double FlorenceLat = 43.77, FlorenceLon = 11.26;
    private const double TokyoLat = 35.68, TokyoLon = 139.65;

    /// <summary>A day's worth of photos at one place, one per hour from 10:00.</summary>
    private static IEnumerable<TripPhoto> Day(DateTime day, double lat, double lon, int count = 8) =>
        Enumerable.Range(0, count).Select(i => new TripPhoto(
            Guid.NewGuid(), day.Date.AddHours(10).AddHours(i), lat, lon, null));

    private static List<TripPhoto> Ordered(params IEnumerable<TripPhoto>[] parts) =>
        parts.SelectMany(p => p).OrderBy(p => p.CapturedAt).ToList();

    [Fact]
    public void Home_IsTheCellWithMostDistinctDays_NotMostPhotos()
    {
        // 70 quiet days at home against one enormous day elsewhere: a 500-shot
        // wedding must not become the place you live.
        var photos = Ordered(
            Enumerable.Range(0, 70).SelectMany(d =>
                Day(new DateTime(2024, 1, 1).AddDays(d), BarcelonaLat, BarcelonaLon, count: 2)),
            Day(new DateTime(2024, 5, 1), RomeLat, RomeLon, count: 500));

        var home = TripClustering.FindHome(photos);

        Assert.NotNull(home);
        Assert.Equal(BarcelonaLat, home!.Value.Lat, precision: 1);
        Assert.Equal(BarcelonaLon, home.Value.Lon, precision: 1);
    }

    [Fact]
    public void Home_IsNullWhenNoCellIsLivedInEnough()
    {
        // Someone who only ever photographs on holiday has no detectable home, and
        // the honest answer is silence — a guessed home makes every local photo a trip.
        var photos = Ordered(
            Enumerable.Range(0, 10).SelectMany(d =>
                Day(new DateTime(2024, 1, 1).AddDays(d), BarcelonaLat, BarcelonaLon)));

        Assert.Null(TripClustering.FindHome(photos));
    }

    [Fact]
    public void Segment_CutsOnALongGap()
    {
        var photos = Ordered(
            Day(new DateTime(2024, 3, 1), RomeLat, RomeLon),
            Day(new DateTime(2024, 3, 8), RomeLat, RomeLon));

        // A week apart at the same place is two segments: same city, different trips.
        Assert.Equal(2, TripClustering.Segment(photos).Count);
    }

    [Fact]
    public void Segment_BridgesAQuietDayInTheSameRegion()
    {
        // Rome, nothing on the 2nd (a rest day), Rome again on the 3rd: last shot
        // 17:00, next 10:00 two mornings later — a 41h gap that is still one trip.
        var photos = Ordered(
            Day(new DateTime(2024, 3, 1), RomeLat, RomeLon),
            Day(new DateTime(2024, 3, 3), RomeLat, RomeLon));

        var segments = TripClustering.Segment(photos);

        Assert.Single(segments);
        Assert.Equal(new DateTime(2024, 3, 1, 10, 0, 0), segments[0].Start);
        Assert.Equal(2, segments[0].DistinctDays);
    }

    [Fact]
    public void Segment_DoesNotBridgeAcrossDistance()
    {
        // Same 34h gap as the quiet night above, but you went home in between.
        var photos = Ordered(
            Day(new DateTime(2024, 3, 1), RomeLat, RomeLon),
            Day(new DateTime(2024, 3, 3), BarcelonaLat, BarcelonaLon));

        Assert.Equal(2, TripClustering.Segment(photos).Count);
    }

    [Fact]
    public void SelectTrips_RejectsWhatHappenedAtHome()
    {
        var segments = TripClustering.Segment(Ordered(
            Day(new DateTime(2024, 3, 1), BarcelonaLat, BarcelonaLon),
            Day(new DateTime(2024, 3, 2), BarcelonaLat, BarcelonaLon)));

        Assert.Empty(TripClustering.SelectTrips(segments, (BarcelonaLat, BarcelonaLon)));
    }

    [Fact]
    public void SelectTrips_RejectsADayOut()
    {
        // Far from home, plenty of photos, but home by midnight: 20 shots between
        // 09:00 and 18:45. A day out is not a trip.
        var dayOut = Enumerable.Range(0, 20).Select(i => new TripPhoto(
            Guid.NewGuid(),
            new DateTime(2024, 3, 1, 9, 0, 0).AddMinutes(i * 30),
            RomeLat, RomeLon, null));

        var segments = TripClustering.Segment(Ordered(dayOut));

        Assert.Empty(TripClustering.SelectTrips(segments, (BarcelonaLat, BarcelonaLon)));
    }

    [Fact]
    public void SelectTrips_AcceptsARealTrip()
    {
        var segments = TripClustering.Segment(Ordered(
            Day(new DateTime(2024, 3, 1), RomeLat, RomeLon),
            Day(new DateTime(2024, 3, 2), RomeLat, RomeLon),
            Day(new DateTime(2024, 3, 3), FlorenceLat, FlorenceLon)));

        var trips = TripClustering.SelectTrips(segments, (BarcelonaLat, BarcelonaLon)).ToList();

        Assert.Single(trips);
        Assert.Equal(3, trips[0].DistinctDays);
        Assert.Equal(24, trips[0].Photos.Count);
    }

    [Fact]
    public void Segment_SurvivesTheTokyoTimezoneJump()
    {
        // CapturedAt is the photo's OWN wall-clock. Flying Barcelona→Tokyo the
        // clock jumps +8h with no real gap; flying back it appears to go backwards.
        // Both must stay one trip — this is why MaxGapWithinTrip is 20h.
        var photos = Ordered(
            // Last shots at the airport, Barcelona time, up to 17:00.
            Day(new DateTime(2024, 4, 1), BarcelonaLat, BarcelonaLon),
            // Landing: the clock has jumped +8h, so the first Tokyo shot reads as
            // 10:00 the next day — 17h later on paper, but no gap in the trip.
            Day(new DateTime(2024, 4, 2), TokyoLat, TokyoLon),
            Day(new DateTime(2024, 4, 3), TokyoLat, TokyoLon),
            Day(new DateTime(2024, 4, 4), TokyoLat, TokyoLon));

        var trips = TripClustering
            .SelectTrips(TripClustering.Segment(photos), (BarcelonaLat, BarcelonaLon))
            .ToList();

        Assert.Single(trips);
        Assert.Equal(4, trips[0].DistinctDays);
    }

    [Fact]
    public void CentroidCell_AbsorbsTheCentroidDriftingAsATripGrows()
    {
        // Tomorrow's photos move the centroid a little; the dedupe key must not
        // move with it, or the same trip would be re-created and lose its history.
        var before = TripClustering.CentroidCell(RomeLat, RomeLon);
        var after = TripClustering.CentroidCell(RomeLat + 0.05, RomeLon - 0.15);

        Assert.Equal(before, after);
    }

    [Fact]
    public void CentroidCell_SeparatesRealDestinations()
    {
        Assert.NotEqual(
            TripClustering.CentroidCell(RomeLat, RomeLon),
            TripClustering.CentroidCell(TokyoLat, TokyoLon));
    }
}
