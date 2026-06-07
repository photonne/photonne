using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Pure-function tests for the ISO 6709 location parser used to read GPS
/// from QuickTime video metadata (com.apple.quicktime.location.ISO6709).
/// </summary>
public sealed class Iso6709ParserTests
{
    [Fact]
    public void Parses_LatLonAltitude_WithTrailingSlash()
    {
        // Format as written by iPhones: +lat-lon+alt/
        var ok = ExifExtractorService.TryParseIso6709("+34.0679-118.4438+010.123/", out var lat, out var lon, out var alt);

        Assert.True(ok);
        Assert.Equal(34.0679, lat, precision: 4);
        Assert.Equal(-118.4438, lon, precision: 4);
        Assert.NotNull(alt);
        Assert.Equal(10.123, alt!.Value, precision: 3);
    }

    [Fact]
    public void Parses_LatLon_WithoutAltitude()
    {
        // Madrid — longitude west of Greenwich must stay negative.
        var ok = ExifExtractorService.TryParseIso6709("+40.4168-003.7038/", out var lat, out var lon, out var alt);

        Assert.True(ok);
        Assert.Equal(40.4168, lat, precision: 4);
        Assert.Equal(-3.7038, lon, precision: 4);
        Assert.Null(alt);
    }

    [Fact]
    public void Parses_SouthernWesternHemisphere_BothNegative()
    {
        // Buenos Aires.
        var ok = ExifExtractorService.TryParseIso6709("-34.6037-058.3816+025.000/", out var lat, out var lon, out var alt);

        Assert.True(ok);
        Assert.Equal(-34.6037, lat, precision: 4);
        Assert.Equal(-58.3816, lon, precision: 4);
        Assert.Equal(25.0, alt!.Value, precision: 1);
    }

    [Fact]
    public void Parses_NullIsland_CallerGuardsAgainstIt()
    {
        // The parser itself accepts (0,0) — the extractor's Null Island
        // guard is what drops it, mirroring the EXIF GpsDirectory branch.
        var ok = ExifExtractorService.TryParseIso6709("+00.0000+000.0000/", out var lat, out var lon, out _);

        Assert.True(ok);
        Assert.Equal(0, lat);
        Assert.Equal(0, lon);
    }

    [Theory]
    [InlineData("")]
    [InlineData("garbage")]
    [InlineData("+12.34/")]          // only one coordinate
    [InlineData("+91.0000+002.0000/")]  // latitude out of range
    [InlineData("+41.0000+181.0000/")]  // longitude out of range
    public void Rejects_MalformedOrOutOfRange(string value)
    {
        var ok = ExifExtractorService.TryParseIso6709(value, out _, out _, out _);

        Assert.False(ok);
    }
}
