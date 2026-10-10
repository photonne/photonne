using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.Geo;

public sealed record InterpolationResult(int Filled, int Skipped)
{
    public static readonly InterpolationResult Empty = new(0, 0);
}

/// <summary>
/// Gives coordinates to photos that have none, by copying them from a geolocated
/// photo the same user took minutes away.
///
/// Why this exists: in a real library only a fraction of photos carry GPS — the
/// phone tags them, the camera doesn't. That doesn't just thin out place data,
/// it BREAKS trip detection, which splits a trip whenever it sees a gap of a day
/// between geolocated photos. A day you happened to shoot on the camera is an
/// invisible hole, and one week in Girona comes out as three separate trips. Not
/// a poorer memory — three wrong ones.
///
/// Why it's defensible rather than invention: a photo is only filled in when it
/// is BRACKETED by two real fixes that agree with each other. Two fixes 20 minutes
/// apart and 300 m apart mean you were standing still, and anything shot between
/// them was shot right there. Two fixes two hours apart and 150 km apart mean you
/// were on a motorway, and where you were in between is genuinely unknown — so
/// nothing is written. That is the whole rule.
/// </summary>
public sealed class LocationInterpolationRunner
{
    /// <summary>How far in time an anchor may be. Wider than this and even a
    /// tight bracket stops meaning much.</summary>
    private static readonly TimeSpan MaxAnchorGap = TimeSpan.FromHours(2);

    /// <summary>
    /// With only one anchor there's no way to tell standing still from driving
    /// away, so the window has to be short enough that it doesn't matter: in ten
    /// minutes you cannot leave the city you were in.
    /// </summary>
    private static readonly TimeSpan MaxSingleAnchorGap = TimeSpan.FromMinutes(10);

    /// <summary>
    /// How far apart the two anchors may be and still count as "the same place".
    /// Generous on purpose — the consumers work at city scale, so 25 km of slack
    /// is invisible to a trip and to a place name, while still ruling out the
    /// motorway case this rule exists to catch.
    /// </summary>
    private const double MaxBracketSpreadMeters = 25_000;

    private const int SaveBatchSize = 500;

    private readonly ApplicationDbContext _db;

    public LocationInterpolationRunner(ApplicationDbContext db) => _db = db;

    /// <summary>Photos with no coordinates that could plausibly get some. A rough
    /// count — it doesn't run the bracket test, so the real fill is lower.</summary>
    public Task<int> CandidateCountAsync(CancellationToken ct) => _db.AssetExifs
        .Where(e => e.Latitude == null
                 && e.Asset.DeletedAt == null
                 && e.Asset.OwnerId != null)
        .CountAsync(ct);

    /// <summary>
    /// Sweeps every owner's timeline once. Idempotent: a filled photo now has
    /// coordinates, so the next pass doesn't see it as a candidate.
    /// </summary>
    public async Task<InterpolationResult> RunAsync(CancellationToken ct)
    {
        var ownerIds = await _db.Assets
            .Where(a => a.OwnerId != null && a.DeletedAt == null)
            .Select(a => a.OwnerId!.Value)
            .Distinct()
            .ToListAsync(ct);

        int filled = 0, skipped = 0;
        foreach (var ownerId in ownerIds)
        {
            ct.ThrowIfCancellationRequested();
            var (f, s) = await RunForOwnerAsync(ownerId, ct);
            filled += f;
            skipped += s;
        }

        return new InterpolationResult(filled, skipped);
    }

    private async Task<(int Filled, int Skipped)> RunForOwnerAsync(Guid ownerId, CancellationToken ct)
    {
        // One ordered pass over the owner's whole timeline. The alternative — a
        // correlated "nearest neighbour in time" subquery per candidate — is a
        // query per photo, and there are a hundred thousand of them.
        var rows = await _db.AssetExifs
            .AsNoTracking()
            .Where(e => e.Asset.OwnerId == ownerId && e.Asset.DeletedAt == null)
            .OrderBy(e => e.Asset.CapturedAt)
            .Select(e => new Row(
                e.Id,
                e.Asset.CapturedAt,
                e.Latitude,
                e.Longitude,
                e.LocationSource))
            .ToListAsync(ct);

        if (rows.Count == 0) return (0, 0);

        // Anchors are EXIF fixes only. Never anchor on an interpolated value:
        // inferences would chain off each other and drift a photo across a
        // county, one 25 km hop at a time.
        var anchors = new List<int>();
        for (var i = 0; i < rows.Count; i++)
            if (rows[i].LocationSource == LocationSource.Exif
                && rows[i].Latitude != null && rows[i].Longitude != null)
                anchors.Add(i);

        if (anchors.Count == 0) return (0, rows.Count(r => r.Latitude == null));

        var decisions = new List<(Guid ExifId, double Lat, double Lon)>();
        var skipped = 0;
        var anchorCursor = 0;

        for (var i = 0; i < rows.Count; i++)
        {
            var row = rows[i];
            if (row.Latitude != null) continue;

            // Advance to the first anchor at or after i; everything before it is
            // the previous anchor. Both pointers only move forward, so the whole
            // sweep is linear.
            while (anchorCursor < anchors.Count && anchors[anchorCursor] < i) anchorCursor++;

            var next = anchorCursor < anchors.Count ? rows[anchors[anchorCursor]] : (Row?)null;
            var prev = anchorCursor > 0 ? rows[anchors[anchorCursor - 1]] : (Row?)null;

            var chosen = Choose(row, prev, next);
            if (chosen is null) { skipped++; continue; }
            decisions.Add((row.ExifId, chosen.Value.Lat, chosen.Value.Lon));
        }

        await ApplyAsync(decisions, ct);
        return (decisions.Count, skipped);
    }

    /// <summary>The rule. Returns null whenever the honest answer is "unknown".</summary>
    private static (double Lat, double Lon)? Choose(Row row, Row? prev, Row? next)
    {
        var prevGap = prev is null ? TimeSpan.MaxValue : row.CapturedAt - prev.Value.CapturedAt;
        var nextGap = next is null ? TimeSpan.MaxValue : next.Value.CapturedAt - row.CapturedAt;

        var prevOk = prev is not null && prevGap <= MaxAnchorGap;
        var nextOk = next is not null && nextGap <= MaxAnchorGap;

        if (prevOk && nextOk)
        {
            var spread = GeoDistance.Meters(
                prev!.Value.Latitude!.Value, prev.Value.Longitude!.Value,
                next!.Value.Latitude!.Value, next.Value.Longitude!.Value);
            // The anchors disagree: the photo was taken somewhere along the way,
            // and "along the way" is not a location.
            if (spread > MaxBracketSpreadMeters) return null;

            // They agree, so either is right. The nearer one in time is the
            // better of two equally good answers.
            var closer = prevGap <= nextGap ? prev.Value : next.Value;
            return (closer.Latitude!.Value, closer.Longitude!.Value);
        }

        // One-sided: only trusted over a short hop.
        if (prevOk && prevGap <= MaxSingleAnchorGap)
            return (prev!.Value.Latitude!.Value, prev.Value.Longitude!.Value);
        if (nextOk && nextGap <= MaxSingleAnchorGap)
            return (next!.Value.Latitude!.Value, next.Value.Longitude!.Value);

        return null;
    }

    private async Task ApplyAsync(List<(Guid ExifId, double Lat, double Lon)> decisions, CancellationToken ct)
    {
        foreach (var chunk in decisions.Chunk(SaveBatchSize))
        {
            ct.ThrowIfCancellationRequested();
            var ids = chunk.Select(d => d.ExifId).ToList();
            var entities = await _db.AssetExifs.Where(e => ids.Contains(e.Id)).ToListAsync(ct);
            var byId = entities.ToDictionary(e => e.Id);

            foreach (var (exifId, lat, lon) in chunk)
            {
                if (!byId.TryGetValue(exifId, out var exif)) continue;
                // Re-checked at write time: the sweep read a snapshot, and an EXIF
                // extraction may have landed a real fix in the meantime.
                if (!exif.CanOverwriteLocation(LocationSource.Interpolated)) continue;

                exif.Latitude = lat;
                exif.Longitude = lon;
                exif.LocationSource = LocationSource.Interpolated;
                // Left for the geocoder, which runs next and will name the place.
                exif.GeocodedAt = null;
            }

            await _db.SaveChangesAsync(ct);
        }
    }

    private readonly record struct Row(
        Guid ExifId,
        DateTime CapturedAt,
        double? Latitude,
        double? Longitude,
        LocationSource LocationSource);
}
