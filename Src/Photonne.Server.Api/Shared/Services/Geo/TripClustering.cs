namespace Photonne.Server.Api.Shared.Services.Geo;

/// <summary>One geolocated photo, reduced to what trip detection needs.</summary>
public readonly record struct TripPhoto(
    Guid AssetId,
    DateTime CapturedAt,
    double Latitude,
    double Longitude,
    Guid? PlaceId);

/// <summary>A candidate run of photos, before the away-from-home test.</summary>
public sealed record TripSegment(
    IReadOnlyList<TripPhoto> Photos,
    DateTime Start,
    DateTime End,
    double CentroidLat,
    double CentroidLon)
{
    public int DistinctDays => Photos.Select(p => p.CapturedAt.Date).Distinct().Count();
}

/// <summary>
/// The pure algorithm behind trip detection: no database, no clock, no DI. Split
/// out so the parts that are easy to get subtly wrong can be tested against
/// hand-built timelines instead of a fixture library.
///
/// Not reusable from Features/Map: that clustering is spatial only, parameterised
/// by zoom, and never looks at time. A trip is a time+place notion — the same two
/// coordinates a year apart are two trips, not one.
/// </summary>
public static class TripClustering
{
    /// <summary>
    /// Grid resolution for finding home, ~28 km at the equator. Coarse on purpose:
    /// home is a town, not an address, and a finer grid would split one
    /// neighbourhood across cells and find no home at all.
    /// </summary>
    public const double HomeCellDegrees = 0.25;

    /// <summary>
    /// Distinct days of photos in one cell before it counts as home. Days, never
    /// photo COUNT: one 500-shot wedding would otherwise make the venue your home
    /// and every photo you take at home a trip.
    /// </summary>
    public const int MinHomeDistinctDays = 60;

    /// <summary>
    /// Split a timeline here.
    ///
    /// Deliberately coarse, and not for tidiness: CapturedAt is the photo's OWN
    /// local wall-clock, so flying Madrid→Tokyo makes it jump +8h with no real
    /// time passing, and flying home creates an 8h gap that never happened. A
    /// tighter threshold would shred every long-haul trip into pieces at exactly
    /// the flights. Do not lower this without thinking that through.
    /// </summary>
    public static readonly TimeSpan MaxGapWithinTrip = TimeSpan.FromHours(20);

    /// <summary>
    /// A quiet day mid-trip: you photographed nothing, and the day after you were
    /// still there. Bridged only when the two sides are in the same region.
    ///
    /// 48h, not 36: a real quiet day runs from the last shot one evening to the
    /// first shot two mornings later, which is ~41h — 36h would have been a rule
    /// that never fired for the case it exists for.
    /// </summary>
    public static readonly TimeSpan MaxBridgeGap = TimeSpan.FromHours(48);

    /// <summary>How close two segments must be to be bridged across a quiet gap.</summary>
    public const double MaxBridgeDistanceMeters = 150_000;

    /// <summary>Nearer than this and you didn't go anywhere.</summary>
    public const double MinDistanceFromHomeMeters = 100_000;

    /// <summary>A day out is not a trip.</summary>
    public const int MinTripDistinctDays = 2;

    /// <summary>Fewer than this and there's no story to tell.</summary>
    public const int MinTripAssets = 15;

    /// <summary>
    /// Where the user lives: the ~28 km cell holding photos on the most DISTINCT
    /// days. Returns null when no cell is lived-in enough to be sure — and that
    /// silence is deliberate. A wrong home turns every photo taken at home into a
    /// trip, which is a far worse feed than an empty one.
    /// </summary>
    public static (double Lat, double Lon)? FindHome(IEnumerable<TripPhoto> photos)
    {
        var byCell = new Dictionary<(int, int), HashSet<DateTime>>();
        var sums = new Dictionary<(int, int), (double Lat, double Lon, int Count)>();

        foreach (var photo in photos)
        {
            var cell = Cell(photo.Latitude, photo.Longitude, HomeCellDegrees);
            if (!byCell.TryGetValue(cell, out var days))
                byCell[cell] = days = new HashSet<DateTime>();
            days.Add(photo.CapturedAt.Date);

            var (lat, lon, count) = sums.GetValueOrDefault(cell);
            sums[cell] = (lat + photo.Latitude, lon + photo.Longitude, count + 1);
        }

        if (byCell.Count == 0) return null;

        var best = byCell.OrderByDescending(kv => kv.Value.Count).First();
        if (best.Value.Count < MinHomeDistinctDays) return null;

        // The cell's own mean, not its corner: a cell centre can be 14 km from
        // where anyone actually lives.
        var (sumLat, sumLon, n) = sums[best.Key];
        return (sumLat / n, sumLon / n);
    }

    /// <summary>
    /// Cuts a timeline into runs, then bridges the runs that are obviously the
    /// same trip. Input must be ordered by CapturedAt.
    /// </summary>
    public static List<TripSegment> Segment(IReadOnlyList<TripPhoto> ordered)
    {
        if (ordered.Count == 0) return [];

        var runs = new List<List<TripPhoto>>();
        var current = new List<TripPhoto> { ordered[0] };

        for (var i = 1; i < ordered.Count; i++)
        {
            if (ordered[i].CapturedAt - ordered[i - 1].CapturedAt > MaxGapWithinTrip)
            {
                runs.Add(current);
                current = [];
            }
            current.Add(ordered[i]);
        }
        runs.Add(current);

        var segments = runs.Select(Build).ToList();
        return Bridge(segments);
    }

    /// <summary>Rejoins neighbouring segments separated by a quiet night in the
    /// same region — a day you simply didn't take photos.</summary>
    private static List<TripSegment> Bridge(List<TripSegment> segments)
    {
        var merged = new List<TripSegment>();
        foreach (var segment in segments)
        {
            if (merged.Count == 0) { merged.Add(segment); continue; }

            var last = merged[^1];
            var gap = segment.Start - last.End;
            var apart = GeoDistance.Meters(
                last.CentroidLat, last.CentroidLon,
                segment.CentroidLat, segment.CentroidLon);

            if (gap <= MaxBridgeGap && apart <= MaxBridgeDistanceMeters)
                merged[^1] = Build(last.Photos.Concat(segment.Photos).ToList());
            else
                merged.Add(segment);
        }
        return merged;
    }

    /// <summary>Which segments are actually trips: far from home, spanning days,
    /// with enough photos to be worth opening.</summary>
    public static IEnumerable<TripSegment> SelectTrips(
        IEnumerable<TripSegment> segments, (double Lat, double Lon) home) =>
        segments.Where(s =>
            s.Photos.Count >= MinTripAssets
            && s.DistinctDays >= MinTripDistinctDays
            && GeoDistance.Meters(home.Lat, home.Lon, s.CentroidLat, s.CentroidLon)
                 > MinDistanceFromHomeMeters);

    /// <summary>
    /// Coarse cell used inside the dedupe key: fine enough to tell two
    /// destinations apart, coarse enough that adding photos to a trip usually
    /// doesn't nudge the centroid into a new cell and orphan the row.
    ///
    /// "Usually", not "never" — a grid can't promise otherwise. A trip whose
    /// centroid sits near a 1° line can cross it as photos are added, which
    /// re-creates the trip and loses its FirstGeneratedAt. The start date carries
    /// most of the key's identity; the cell is only here to separate two trips
    /// that begin on the same day, which needs a boundary the trip straddles AND
    /// a same-day neighbour to actually bite.
    /// </summary>
    public static string CentroidCell(double lat, double lon)
    {
        var (cellLat, cellLon) = Cell(lat, lon, 1.0);
        return $"{cellLat}_{cellLon}";
    }

    private static (int, int) Cell(double lat, double lon, double size) =>
        ((int)Math.Floor(lat / size), (int)Math.Floor(lon / size));

    private static TripSegment Build(IReadOnlyList<TripPhoto> photos) => new(
        photos,
        photos[0].CapturedAt,
        photos[^1].CapturedAt,
        photos.Average(p => p.Latitude),
        photos.Average(p => p.Longitude));
}
