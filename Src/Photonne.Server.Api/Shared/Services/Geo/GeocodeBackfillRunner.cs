using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;

namespace Photonne.Server.Api.Shared.Services.Geo;

public sealed record GeocodeBackfillResult(int Processed, int Matched, int Pending);

/// <summary>
/// Geocodes photos indexed before the dataset existed.
///
/// Its own runner rather than MlBackfillRunner: that one filters to
/// <c>Type == AssetType.Image</c> and enqueues AssetEnrichmentTask rows for the
/// ML pipeline. Geocoding applies to videos too and does no work worth queueing —
/// it's a dictionary lookup, so it just runs here.
///
/// Resumability comes from the data, not a cursor: "has coordinates, was never
/// looked at" is a query, so an interrupted run simply picks up where it stopped.
/// </summary>
public sealed class GeocodeBackfillRunner
{
    /// <summary>Batch size for the write loop. Big enough that the round-trips
    /// don't dominate, small enough that a cancel lands quickly.</summary>
    private const int BatchSize = 500;

    private readonly ApplicationDbContext _db;
    private readonly PlaceResolver _resolver;

    public GeocodeBackfillRunner(ApplicationDbContext db, PlaceResolver resolver)
    {
        _db = db;
        _resolver = resolver;
    }

    /// <summary>How many geolocated photos have never been through the geocoder.</summary>
    public Task<int> PendingCountAsync(CancellationToken ct) =>
        PendingQuery().CountAsync(ct);

    /// <summary>
    /// Processes up to <paramref name="max"/> pending rows (null = all).
    /// No-ops when no dataset is baked in — otherwise it would stamp every photo
    /// as "geocoded, found nothing" and they'd all be skipped once one arrived.
    /// </summary>
    public async Task<GeocodeBackfillResult> RunAsync(int? max, CancellationToken ct)
    {
        if (!_resolver.IsAvailable)
            return new GeocodeBackfillResult(0, 0, await PendingCountAsync(ct));

        int processed = 0, matched = 0;

        while (max is null || processed < max.Value)
        {
            ct.ThrowIfCancellationRequested();

            var take = max is null ? BatchSize : Math.Min(BatchSize, max.Value - processed);
            var batch = await PendingQuery().Take(take).ToListAsync(ct);
            if (batch.Count == 0) break;

            foreach (var exif in batch)
            {
                ct.ThrowIfCancellationRequested();
                var resolved = await _resolver.ResolveAsync(exif.Latitude, exif.Longitude, ct);
                exif.PlaceId = resolved.PlaceId;
                exif.GeocodeDistanceMeters = resolved.DistanceMeters;
                // Stamped whether or not we matched: the mark means "looked at",
                // so an unmatchable coordinate leaves the pending set for good.
                exif.GeocodedAt = DateTime.UtcNow;
                processed++;
                if (resolved.PlaceId != null) matched++;
            }

            await _db.SaveChangesAsync(ct);
        }

        return new GeocodeBackfillResult(processed, matched, await PendingCountAsync(ct));
    }

    private IQueryable<Models.AssetExif> PendingQuery() => _db.AssetExifs
        .Where(e => e.GeocodedAt == null
                 && e.Latitude != null
                 && e.Longitude != null
                 // GeoNames' null island, same guard the map endpoints use: a
                 // (0,0) fix is a missing fix, not a spot in the Gulf of Guinea.
                 && !(e.Latitude == 0 && e.Longitude == 0));
}
