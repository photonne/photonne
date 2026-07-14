namespace Photonne.Server.Api.Shared.Services.Geo;

/// <summary>Great-circle distance. (Features/Map has its own copy from before
/// this namespace existed; left alone rather than churn a working endpoint.)</summary>
public static class GeoDistance
{
    private const double EarthRadiusMeters = 6_371_000;

    public static double Meters(double lat1, double lon1, double lat2, double lon2)
    {
        var dLat = ToRadians(lat2 - lat1);
        var dLon = ToRadians(lon2 - lon1);
        var a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2)
              + Math.Cos(ToRadians(lat1)) * Math.Cos(ToRadians(lat2))
              * Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
        return 2 * EarthRadiusMeters * Math.Asin(Math.Min(1, Math.Sqrt(a)));
    }

    private static double ToRadians(double degrees) => degrees * Math.PI / 180;
}
