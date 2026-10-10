using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.Geo;

/// <summary>Outcome of geocoding one photo: the place we landed on (or none) and
/// how far it was.</summary>
public sealed record ResolvedPlace(Guid? PlaceId, int? DistanceMeters);

/// <summary>
/// Turns a coordinate into a persisted <see cref="Place"/>, materializing the
/// row the first time the library reaches a given city.
/// </summary>
public sealed class PlaceResolver
{
    private readonly ApplicationDbContext _db;
    private readonly ReverseGeocoder _geocoder;

    public PlaceResolver(ApplicationDbContext db, ReverseGeocoder geocoder)
    {
        _db = db;
        _geocoder = geocoder;
    }

    public bool IsAvailable => _geocoder.IsAvailable;

    /// <summary>
    /// Resolves and persists. Returns an empty result when there's no dataset, no
    /// match, or the nearest city is beyond the lookup's cap — all three are
    /// "we don't know where this is", which is a perfectly good answer.
    /// </summary>
    public async Task<ResolvedPlace> ResolveAsync(double? latitude, double? longitude, CancellationToken ct)
    {
        if (latitude is null || longitude is null) return new ResolvedPlace(null, null);

        var match = _geocoder.Nearest(latitude.Value, longitude.Value);
        if (match is null) return new ResolvedPlace(null, null);

        var placeId = await GetOrCreatePlaceAsync(match, ct);
        return new ResolvedPlace(placeId, match.DistanceMeters);
    }

    private async Task<Guid> GetOrCreatePlaceAsync(GeoMatch match, CancellationToken ct)
    {
        var existing = await _db.Places
            .Where(p => p.GeonameId == match.GeonameId)
            .Select(p => (Guid?)p.Id)
            .FirstOrDefaultAsync(ct);
        if (existing.HasValue) return existing.Value;

        var place = new Place
        {
            GeonameId = match.GeonameId,
            Name = match.Name,
            CountryCode = match.CountryCode,
            Latitude = match.Latitude,
            Longitude = match.Longitude,
        };
        _db.Places.Add(place);

        try
        {
            await _db.SaveChangesAsync(ct);
            return place.Id;
        }
        catch (DbUpdateException)
        {
            // Enrichment runs several workers over one library, so two photos in
            // the same city can race to create it. The unique index on GeonameId
            // is what makes that safe: whoever loses re-reads the winner's row.
            _db.Entry(place).State = EntityState.Detached;
            return await _db.Places
                .Where(p => p.GeonameId == match.GeonameId)
                .Select(p => p.Id)
                .FirstAsync(ct);
        }
    }
}
