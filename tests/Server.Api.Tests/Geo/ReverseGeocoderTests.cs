using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Tests.Geo;

/// <summary>
/// The grid lookup, with no database and no dataset file. The cases that matter
/// are the ones where geo code traditionally breaks: the antimeridian, the poles,
/// and the middle of an ocean.
/// </summary>
public class ReverseGeocoderTests
{
    private static ReverseGeocoder WithCities(params (string Name, double Lat, double Lon)[] cities) =>
        new(cities.Select((c, i) => new GeoCity(i + 1, c.Name, "ES", c.Lat, c.Lon, 1000)));

    [Fact]
    public void Nearest_PicksTheClosestCity()
    {
        var geocoder = WithCities(
            ("Girona", 41.98, 2.82),
            ("Barcelona", 41.39, 2.17),
            ("Figueres", 42.27, 2.96));

        // A point just outside Girona.
        var match = geocoder.Nearest(41.95, 2.80);

        Assert.NotNull(match);
        Assert.Equal("Girona", match!.Name);
        Assert.InRange(match.DistanceMeters, 0, 5_000);
    }

    [Fact]
    public void Nearest_CrossesTheAntimeridian()
    {
        // Two cells apart on the grid, a few km apart on the planet. A search
        // that treats longitude as a plain number instead of a ring finds
        // nothing here, and Fiji gets no place names.
        var geocoder = WithCities(("Taveuni", -16.85, 179.97));

        var match = geocoder.Nearest(-16.85, -179.98);

        Assert.NotNull(match);
        Assert.Equal("Taveuni", match!.Name);
        Assert.InRange(match.DistanceMeters, 0, 10_000);
    }

    [Fact]
    public void Nearest_HandlesThePoles()
    {
        // Latitude must clamp where longitude wraps: there is no cell north of
        // the pole, and wrapping one in would search the far side of the planet.
        var geocoder = WithCities(("Alert", 82.50, -62.35));

        var match = geocoder.Nearest(82.51, -62.30);

        Assert.NotNull(match);
        Assert.Equal("Alert", match!.Name);
    }

    [Fact]
    public void Nearest_ReturnsNothingInTheMiddleOfTheOcean()
    {
        var geocoder = WithCities(("Lisboa", 38.72, -9.14));

        // Mid-Atlantic. The nearest inhabited speck is thousands of km away, and
        // labelling this "Lisboa" would be a lie the UI would repeat forever.
        var match = geocoder.Nearest(35.0, -40.0);

        Assert.Null(match);
    }

    [Fact]
    public void Nearest_StopsAtTheDistanceCap()
    {
        var geocoder = WithCities(("Barcelona", 41.39, 2.17));

        // ~90 km out: still plausibly "near Barcelona".
        Assert.NotNull(geocoder.Nearest(42.19, 2.17));
        // ~220 km out: not Barcelona in any sense a person would accept.
        Assert.Null(geocoder.Nearest(43.37, 2.17));
    }

    [Fact]
    public void Nearest_IgnoresNullIsland()
    {
        var geocoder = WithCities(("Accra", 5.55, -0.20));

        // (0,0) is how "no GPS fix" reaches the database, not a place off Ghana.
        Assert.Null(geocoder.Nearest(0, 0));
    }

    [Fact]
    public void Nearest_RejectsImpossibleCoordinates()
    {
        var geocoder = WithCities(("Barcelona", 41.39, 2.17));

        Assert.Null(geocoder.Nearest(91, 0));
        Assert.Null(geocoder.Nearest(0, 181));
        Assert.Null(geocoder.Nearest(double.NaN, 2.17));
    }

    [Fact]
    public void Nearest_BreaksTiesByPopulation()
    {
        // Same spot, two names: the one people have heard of is the better label.
        var geocoder = new ReverseGeocoder(new[]
        {
            new GeoCity(1, "Hamlet", "ES", 41.39, 2.17, 600),
            new GeoCity(2, "Barcelona", "ES", 41.39, 2.17, 1_600_000),
        });

        var match = geocoder.Nearest(41.39, 2.17);

        Assert.Equal("Barcelona", match!.Name);
    }

    [Fact]
    public void Nearest_FindsACityAcrossACellBoundary()
    {
        // The point sits in cell (41,2); the city is metres away in cell (42,2).
        // Searching only the containing cell would miss it — which is why the
        // ring search starts at the centre and widens.
        var geocoder = WithCities(("Vic", 42.001, 2.25));

        var match = geocoder.Nearest(41.999, 2.25);

        Assert.NotNull(match);
        Assert.Equal("Vic", match!.Name);
        Assert.InRange(match.DistanceMeters, 0, 1_000);
    }

    [Fact]
    public void WithoutADataset_EverythingIsNullAndNothingThrows()
    {
        // The state of every dev box and of any image whose build couldn't reach
        // GeoNames. Place names disappear; nothing else does.
        var geocoder = new ReverseGeocoder("/nonexistent/cities.tsv.gz");

        Assert.False(geocoder.IsAvailable);
        Assert.Null(geocoder.DatasetDate);
        Assert.Null(geocoder.Nearest(41.39, 2.17));
    }

    [Fact]
    public void AnEmptyDatasetFileIsTreatedAsAbsent()
    {
        // The image bakes a zero-byte placeholder when the download fails, so
        // this is a build outcome, not corruption.
        var path = Path.Combine(Path.GetTempPath(), $"cities-{Guid.NewGuid():N}.tsv.gz");
        File.WriteAllBytes(path, []);
        try
        {
            var geocoder = new ReverseGeocoder(path);
            Assert.False(geocoder.IsAvailable);
            Assert.Null(geocoder.Nearest(41.39, 2.17));
        }
        finally
        {
            File.Delete(path);
        }
    }
}
