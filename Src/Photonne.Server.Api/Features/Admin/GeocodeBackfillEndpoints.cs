using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>Admin-only: resolves place names for photos indexed before the
/// GeoNames dataset was available. Mirrors the ML backfill endpoints' shape.</summary>
public class GeocodeBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("/reverse-geocode/pending-count", async (
            [FromServices] GeocodeBackfillRunner runner,
            [FromServices] ReverseGeocoder geocoder,
            CancellationToken ct) =>
        {
            var pending = await runner.PendingCountAsync(ct);
            return Results.Ok(new
            {
                pending,
                // Surfaced so the admin UI can explain a backfill that does
                // nothing, instead of leaving it looking broken.
                datasetAvailable = geocoder.IsAvailable,
                cities = geocoder.IsAvailable ? geocoder.CityCount : 0,
            });
        })
        .WithName("GetReverseGeocodePendingCount")
        .WithDescription("How many geolocated assets still have no resolved place");

        group.MapPost("/reverse-geocode/backfill", async (
            [FromServices] GeocodeBackfillRunner runner,
            [FromServices] ReverseGeocoder geocoder,
            [FromQuery] int? batchSize,
            CancellationToken ct) =>
        {
            if (!geocoder.IsAvailable)
                return Results.Ok(new
                {
                    processed = 0,
                    matched = 0,
                    pending = await runner.PendingCountAsync(ct),
                    message = "No hay dataset de lugares en esta imagen; no se ha geocodificado nada.",
                });

            var result = await runner.RunAsync(batchSize, ct);
            return Results.Ok(new
            {
                result.Processed,
                result.Matched,
                result.Pending,
                message = $"Geocodificados {result.Processed} asset(s), {result.Matched} con lugar. Quedan {result.Pending}.",
            });
        })
        .WithName("RunReverseGeocodeBackfill")
        .WithDescription("Resolves place names for geolocated assets that have none");
    }
}
