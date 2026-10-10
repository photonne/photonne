using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.Geo;

public sealed record TripDetectionResult(int Created, int Updated, int Removed, bool HomeFound)
{
    public static readonly TripDetectionResult NoHome = new(0, 0, 0, false);
}

/// <summary>
/// Finds a user's trips and persists them. Runs after geocoding, so the places a
/// trip is named from are already resolved.
/// </summary>
public sealed class TripDetectionService
{
    /// <summary>How far back to look for home. Long enough to be stable, short
    /// enough that a move two years ago doesn't keep haunting the answer.</summary>
    private const int HomeLookbackYears = 2;

    private readonly ApplicationDbContext _db;

    public TripDetectionService(ApplicationDbContext db) => _db = db;

    public async Task<TripDetectionResult> RunForUserAsync(Guid userId, DateTime localToday, CancellationToken ct)
    {
        // Own photos only. A trip is personal — a shared folder full of someone
        // else's holiday is their trip, not yours — and home is the most personal
        // notion of all.
        var photos = await _db.AssetExifs
            .AsNoTracking()
            .Where(e => e.Asset.OwnerId == userId
                     && e.Asset.DeletedAt == null
                     && !e.Asset.IsArchived
                     && !e.Asset.IsFileMissing
                     && !e.Asset.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart)
                     && e.Latitude != null
                     && e.Longitude != null)
            .OrderBy(e => e.Asset.CapturedAt)
            .Select(e => new TripPhoto(
                e.AssetId,
                e.Asset.CapturedAt,
                e.Latitude!.Value,
                e.Longitude!.Value,
                e.PlaceId))
            .ToListAsync(ct);

        if (photos.Count == 0) return TripDetectionResult.NoHome;

        var since = localToday.AddYears(-HomeLookbackYears);
        var home = TripClustering.FindHome(photos.Where(p => p.CapturedAt >= since));

        // No confident home means no reference point for "away", and guessing one
        // would turn every photo taken at home into a trip. Nothing is worse than
        // a feed full of "trips" to your own street.
        if (home is null) return TripDetectionResult.NoHome;

        var trips = TripClustering
            .SelectTrips(TripClustering.Segment(photos), home.Value)
            .ToList();

        var placeNames = await LoadPlaceNamesAsync(trips, ct);
        return await PersistAsync(userId, trips, placeNames, ct);
    }

    private async Task<Dictionary<Guid, string>> LoadPlaceNamesAsync(
        List<TripSegment> trips, CancellationToken ct)
    {
        var placeIds = trips
            .SelectMany(t => t.Photos)
            .Select(p => p.PlaceId)
            .OfType<Guid>()
            .Distinct()
            .ToList();

        if (placeIds.Count == 0) return [];

        return await _db.Places
            .AsNoTracking()
            .Where(p => placeIds.Contains(p.Id))
            .ToDictionaryAsync(p => p.Id, p => p.Name, ct);
    }

    private async Task<TripDetectionResult> PersistAsync(
        Guid userId,
        List<TripSegment> segments,
        Dictionary<Guid, string> placeNames,
        CancellationToken ct)
    {
        var existing = await _db.Trips
            .Include(t => t.Places)
            .Where(t => t.OwnerId == userId)
            .ToDictionaryAsync(t => t.DedupeKey, ct);

        int created = 0, updated = 0;
        var seen = new HashSet<string>();

        foreach (var segment in segments)
        {
            ct.ThrowIfCancellationRequested();

            var counts = segment.Photos
                .Select(p => p.PlaceId)
                .OfType<Guid>()
                .GroupBy(id => id)
                .ToDictionary(g => g.Key, g => g.Count());

            var title = TripNaming.Title(
                counts
                    .Where(kv => placeNames.ContainsKey(kv.Key))
                    .Select(kv => new TripPlaceCount(placeNames[kv.Key], kv.Value))
                    .ToList(),
                segment.Start);

            // Start date + coarse cell: a trip that grows by a day, or gains a
            // better name once more of its photos are geocoded, stays the same row.
            var key = $"trip:{segment.Start:yyyy-MM-dd}:{TripClustering.CentroidCell(segment.CentroidLat, segment.CentroidLon)}";
            seen.Add(key);

            if (existing.TryGetValue(key, out var trip))
            {
                trip.Title = title;
                trip.WindowStart = segment.Start;
                trip.WindowEnd = segment.End;
                trip.CentroidLat = segment.CentroidLat;
                trip.CentroidLon = segment.CentroidLon;
                trip.AssetCount = segment.Photos.Count;
                SyncPlaces(trip, counts);
                updated++;
            }
            else
            {
                trip = new Trip
                {
                    OwnerId = userId,
                    Title = title,
                    WindowStart = segment.Start,
                    WindowEnd = segment.End,
                    CentroidLat = segment.CentroidLat,
                    CentroidLon = segment.CentroidLon,
                    AssetCount = segment.Photos.Count,
                    DedupeKey = key,
                };
                _db.Trips.Add(trip);
                SyncPlaces(trip, counts);
                created++;
            }
        }

        var stale = existing.Values.Where(t => !seen.Contains(t.DedupeKey)).ToList();
        _db.Trips.RemoveRange(stale);

        await _db.SaveChangesAsync(ct);
        return new TripDetectionResult(created, updated, stale.Count, HomeFound: true);
    }

    /// <summary>
    /// Diffs the trip's places rather than replacing them wholesale. Clearing and
    /// re-adding would delete and insert the same (TripId, PlaceId) in one
    /// SaveChanges — and that pair is the primary key, so the pass would fail the
    /// moment a trip kept a place it already had, which is every night after the
    /// first.
    /// </summary>
    private void SyncPlaces(Trip trip, Dictionary<Guid, int> counts)
    {
        foreach (var existing in trip.Places.ToList())
        {
            if (counts.TryGetValue(existing.PlaceId, out var count))
                existing.AssetCount = count;
            else
            {
                trip.Places.Remove(existing);
                _db.TripPlaces.Remove(existing);
            }
        }

        var known = trip.Places.Select(p => p.PlaceId).ToHashSet();
        foreach (var (placeId, count) in counts.Where(kv => !known.Contains(kv.Key)))
            trip.Places.Add(new TripPlace
            {
                TripId = trip.Id,
                PlaceId = placeId,
                AssetCount = count,
            });
    }
}
