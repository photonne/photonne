using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>
/// Admin-only diagnostics for reverse geocoding.
///
/// Read-only on purpose: the work itself is a maintenance kind
/// ("reverse-geocode", see MaintenanceService.Memories), so it gets progress,
/// cancellation and a task that outlives the connection. This endpoint answers
/// the question that comes first — is the dataset even in this image? — which
/// nothing else can, and which decides whether a backfill would do anything.
/// </summary>
public class GeocodeBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("/reverse-geocode/pending-count", async Task<Ok<ReverseGeocodePendingCountResponse>> (
            [FromServices] GeocodeBackfillRunner runner,
            [FromServices] ReverseGeocoder geocoder,
            CancellationToken ct) =>
        {
            var pending = await runner.PendingCountAsync(ct);
            return TypedResults.Ok(new ReverseGeocodePendingCountResponse(
                pending,
                // Surfaced so the admin UI can explain a backfill that does
                // nothing, instead of leaving it looking broken.
                DatasetAvailable: geocoder.IsAvailable,
                Cities: geocoder.IsAvailable ? geocoder.CityCount : 0));
        })
        .WithName("GetReverseGeocodePendingCount")
        .WithDescription("How many geolocated assets still have no resolved place, and whether the dataset is present");
    }
}

public sealed record ReverseGeocodePendingCountResponse(int Pending, bool DatasetAvailable, int Cities);
