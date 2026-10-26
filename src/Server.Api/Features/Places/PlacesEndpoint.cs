using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Places;

/// <summary>A place the caller has photos of, with how many and the newest one as cover.</summary>
public record PlaceDto(Guid Id, string Name, string CountryCode, int AssetCount, Guid CoverAssetId);

/// <summary>
/// The places (GeoNames cities, resolved at index time) the caller's photos
/// were taken in, most photographed first. Powers the search page's "Lugares"
/// row; each one is a search by <c>placeId</c>. Scoped like the scene and
/// object facets: the caller's own photos, not deleted, archived or missing.
/// </summary>
public class PlacesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/places", Handle)
            .WithName("ListPlaces")
            .WithTags("Places")
            .WithDescription("Lists the places the caller's photos were taken in, with counts, most photographed first")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<List<PlaceDto>>, UnauthorizedHttpResult>> Handle(
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        [FromQuery] int? limit,
        CancellationToken ct)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(claim?.Value, out var userId)) return TypedResults.Unauthorized();

        var take = Math.Clamp(limit ?? 50, 1, 500);

        var counts = await db.Assets.AsNoTracking()
            .Where(a => a.OwnerId == userId && a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                        && a.Exif != null && a.Exif.PlaceId != null)
            .GroupBy(a => a.Exif!.PlaceId!.Value)
            .Select(g => new
            {
                PlaceId = g.Key,
                Count = g.Count(),
                Cover = g.OrderByDescending(a => a.CapturedAt).ThenBy(a => a.Id).Select(a => a.Id).First()
            })
            .OrderByDescending(x => x.Count)
            .ThenBy(x => x.PlaceId)
            .Take(take)
            .ToListAsync(ct);

        var ids = counts.Select(c => c.PlaceId).ToList();
        var places = await db.Places.AsNoTracking()
            .Where(p => ids.Contains(p.Id))
            .ToDictionaryAsync(p => p.Id, ct);

        var result = counts
            .Where(c => places.ContainsKey(c.PlaceId))
            .Select(c =>
            {
                var place = places[c.PlaceId];
                return new PlaceDto(place.Id, place.Name, place.CountryCode, c.Count, c.Cover);
            })
            .ToList();

        return TypedResults.Ok(result);
    }
}
