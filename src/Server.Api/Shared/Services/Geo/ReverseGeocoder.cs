using System.IO.Compression;
using System.Globalization;

namespace Photonne.Server.Api.Shared.Services.Geo;

/// <summary>One row of the baked GeoNames extract.</summary>
internal readonly record struct GeoCity(
    int GeonameId,
    string Name,
    string CountryCode,
    double Latitude,
    double Longitude,
    long Population);

/// <summary>What a lookup found: the city and how far the photo was from it.</summary>
public sealed record GeoMatch(
    int GeonameId,
    string Name,
    string CountryCode,
    double Latitude,
    double Longitude,
    int DistanceMeters);

/// <summary>
/// Offline nearest-city lookup over the GeoNames cities500 extract baked into
/// the image (see the geodata stage in the Dockerfile).
///
/// Offline on purpose: a per-photo call to Nominatim would rate-limit a first
/// index into oblivion, add a network dependency to an otherwise self-contained
/// server, and quietly ship every user's coordinates to a third party.
///
/// The index is a 1°x1° equirectangular grid, not a k-d tree. A cell is at most
/// 111 km tall and this runs once per photo, ever — the tree's better asymptotics
/// would buy microseconds no one is waiting for, in exchange for a lot more code
/// to get wrong. The grid also makes the ring search trivial to reason about at
/// the antimeridian and the poles, which is where geo code usually breaks.
///
/// Missing dataset = every lookup returns null. Dev boxes and the test suite run
/// without the file, and place names simply don't appear.
/// </summary>
public sealed class ReverseGeocoder
{
    /// <summary>
    /// Beyond this, the nearest "city" is not where the photo was taken — it's
    /// the nearest inhabited speck to a boat, a desert or a glacier. Better to
    /// say nothing than to label an Atlantic crossing with the Azores.
    /// </summary>
    private const double MaxDistanceMeters = 100_000;

    private const double EarthRadiusMeters = 6_371_000;

    /// <summary>GeoNames' own null island: rows at exactly (0,0) are missing
    /// coordinates, not a place in the Gulf of Guinea. Mirrors the guard the map
    /// endpoints already apply to assets.</summary>
    private static bool IsNullIsland(double lat, double lon) => lat == 0 && lon == 0;

    private readonly string? _path;
    private readonly Lazy<Dictionary<(int Lat, int Lon), List<GeoCity>>> _grid;

    /// <summary>Production: loads lazily from <paramref name="path"/> on first
    /// lookup, so a server that never indexes a geotagged photo never pays for
    /// the parse.</summary>
    public ReverseGeocoder(string? path)
    {
        _path = path;
        _grid = new Lazy<Dictionary<(int, int), List<GeoCity>>>(LoadFromFile);
    }

    /// <summary>Tests: an explicit set of cities, no file involved.</summary>
    internal ReverseGeocoder(IEnumerable<GeoCity> cities)
    {
        _path = null;
        var grid = BuildGrid(cities);
        _grid = new Lazy<Dictionary<(int, int), List<GeoCity>>>(() => grid);
    }

    /// <summary>True when a dataset is actually loaded. False on a dev box with
    /// no cities file — callers use it to log once rather than per photo.</summary>
    public bool IsAvailable => _grid.Value.Count > 0;

    public int CityCount => _grid.Value.Sum(kv => kv.Value.Count);

    /// <summary>
    /// When this image's copy of the dataset was fetched — the file's mtime,
    /// which the Docker build stage stamps at download time. GeoNames publishes
    /// daily and the dump carries no version of its own, so this is the only
    /// handle an operator has on "how old are my place names".
    /// </summary>
    public DateTime? DatasetDate
    {
        get
        {
            if (!IsAvailable || string.IsNullOrWhiteSpace(_path)) return null;
            try { return File.GetLastWriteTimeUtc(_path); }
            catch { return null; }
        }
    }

    /// <summary>
    /// Nearest city within <see cref="MaxDistanceMeters"/>, or null.
    ///
    /// Searches the cell containing the point, then widens by one ring at a time.
    /// A ring is only worth searching while its nearest possible point could beat
    /// the best match so far — that's the `ringFloor` check, and it's what stops
    /// this from degenerating into a full scan over empty ocean.
    /// </summary>
    public GeoMatch? Nearest(double latitude, double longitude)
    {
        if (!IsAvailable) return null;
        if (double.IsNaN(latitude) || double.IsNaN(longitude)) return null;
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
        if (IsNullIsland(latitude, longitude)) return null;

        var grid = _grid.Value;
        var centreLat = (int)Math.Floor(latitude);
        var centreLon = (int)Math.Floor(longitude);

        GeoCity? best = null;
        var bestMeters = double.MaxValue;

        // A degree of latitude is ~111 km everywhere, so once the ring's own
        // floor exceeds the cap there is nothing left worth looking at.
        var maxRing = (int)Math.Ceiling(MaxDistanceMeters / 111_000.0) + 1;

        for (var ring = 0; ring <= maxRing; ring++)
        {
            // Nearest possible point in this ring, in metres. Ring 0 is the cell
            // we're standing in, so its floor is 0.
            var ringFloor = ring == 0 ? 0 : (ring - 1) * 111_000.0;
            if (best != null && ringFloor > bestMeters) break;
            if (ringFloor > MaxDistanceMeters) break;

            foreach (var (cellLat, cellLon) in RingCells(centreLat, centreLon, ring))
            {
                if (!grid.TryGetValue((cellLat, cellLon), out var cities)) continue;

                foreach (var city in cities)
                {
                    var meters = HaversineMeters(latitude, longitude, city.Latitude, city.Longitude);
                    if (meters > MaxDistanceMeters) continue;
                    // Population breaks ties: two cities at the same distance,
                    // the one people have heard of is the better label.
                    var better = meters < bestMeters
                        || (meters == bestMeters && best != null && city.Population > best.Value.Population);
                    if (!better) continue;
                    best = city;
                    bestMeters = meters;
                }
            }
        }

        if (best is null) return null;
        return new GeoMatch(
            best.Value.GeonameId,
            best.Value.Name,
            best.Value.CountryCode,
            best.Value.Latitude,
            best.Value.Longitude,
            (int)Math.Round(bestMeters));
    }

    /// <summary>
    /// The cells exactly <paramref name="ring"/> steps from the centre — the
    /// square's perimeter, not its area, so widening never re-checks a cell.
    ///
    /// Longitude wraps: at ±180 the neighbouring cell is on the other side of the
    /// antimeridian, and a photo taken in Fiji must still find Fiji. Latitude
    /// clamps instead: there is no cell north of the pole, and wrapping there
    /// would teleport the search to the opposite hemisphere.
    /// </summary>
    private static IEnumerable<(int Lat, int Lon)> RingCells(int centreLat, int centreLon, int ring)
    {
        if (ring == 0)
        {
            yield return (centreLat, WrapLon(centreLon));
            yield break;
        }

        for (var dLat = -ring; dLat <= ring; dLat++)
        {
            var lat = centreLat + dLat;
            if (lat < -90 || lat > 89) continue;

            var onLatEdge = Math.Abs(dLat) == ring;
            if (onLatEdge)
            {
                // Full row.
                for (var dLon = -ring; dLon <= ring; dLon++)
                    yield return (lat, WrapLon(centreLon + dLon));
            }
            else
            {
                // Just the two sides.
                yield return (lat, WrapLon(centreLon - ring));
                yield return (lat, WrapLon(centreLon + ring));
            }
        }
    }

    /// <summary>Folds a longitude cell index into [-180, 179].</summary>
    private static int WrapLon(int lon)
    {
        var wrapped = (lon + 180) % 360;
        if (wrapped < 0) wrapped += 360;
        return wrapped - 180;
    }

    private static double HaversineMeters(double lat1, double lon1, double lat2, double lon2)
    {
        var dLat = ToRadians(lat2 - lat1);
        var dLon = ToRadians(lon2 - lon1);
        var a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2)
              + Math.Cos(ToRadians(lat1)) * Math.Cos(ToRadians(lat2))
              * Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
        return 2 * EarthRadiusMeters * Math.Asin(Math.Min(1, Math.Sqrt(a)));
    }

    private static double ToRadians(double degrees) => degrees * Math.PI / 180;

    private static Dictionary<(int, int), List<GeoCity>> BuildGrid(IEnumerable<GeoCity> cities)
    {
        var grid = new Dictionary<(int, int), List<GeoCity>>();
        foreach (var city in cities)
        {
            var key = ((int)Math.Floor(city.Latitude), (int)Math.Floor(city.Longitude));
            if (!grid.TryGetValue(key, out var bucket))
                grid[key] = bucket = new List<GeoCity>();
            bucket.Add(city);
        }
        return grid;
    }

    /// <summary>
    /// Parses the baked TSV: geonameid, name, lat, lon, country, population.
    /// Any failure — no file, truncated download, unreadable path — degrades to
    /// an empty index rather than taking the server down with it. Place names are
    /// a nice-to-have; booting is not.
    /// </summary>
    private Dictionary<(int, int), List<GeoCity>> LoadFromFile()
    {
        if (string.IsNullOrWhiteSpace(_path) || !File.Exists(_path))
        {
            Console.WriteLine($"[GEO] No cities dataset at '{_path}'. Place names disabled.");
            return new Dictionary<(int, int), List<GeoCity>>();
        }

        // The image bakes an empty placeholder when the build couldn't reach
        // GeoNames, so this is the expected shape of "the download failed" —
        // not corruption worth an error.
        if (new FileInfo(_path).Length == 0)
        {
            Console.WriteLine($"[GEO] Cities dataset at '{_path}' is empty (download skipped at build time). Place names disabled.");
            return new Dictionary<(int, int), List<GeoCity>>();
        }

        try
        {
            var cities = new List<GeoCity>(capacity: 200_000);
            using var file = File.OpenRead(_path);
            using var gzip = new GZipStream(file, CompressionMode.Decompress);
            using var reader = new StreamReader(gzip);

            string? line;
            while ((line = reader.ReadLine()) != null)
            {
                var parts = line.Split('\t');
                if (parts.Length < 6) continue;
                if (!int.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out var id)) continue;
                if (!double.TryParse(parts[2], NumberStyles.Float, CultureInfo.InvariantCulture, out var lat)) continue;
                if (!double.TryParse(parts[3], NumberStyles.Float, CultureInfo.InvariantCulture, out var lon)) continue;
                if (IsNullIsland(lat, lon)) continue;
                long.TryParse(parts[5], NumberStyles.Integer, CultureInfo.InvariantCulture, out var population);

                cities.Add(new GeoCity(id, parts[1], parts[4], lat, lon, population));
            }

            Console.WriteLine($"[GEO] Loaded {cities.Count} cities from '{_path}'.");
            return BuildGrid(cities);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[GEO] Failed to load '{_path}': {ex.Message}. Place names disabled.");
            return new Dictionary<(int, int), List<GeoCity>>();
        }
    }
}
