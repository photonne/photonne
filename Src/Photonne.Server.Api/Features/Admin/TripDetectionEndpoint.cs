using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>Admin-only: re-runs trip detection for every active user now, instead
/// of waiting for the nightly pass. Same code path as
/// NightlySchedulerService.RunTripDetectionAsync.</summary>
public class TripDetectionEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/trips/detect", Handle)
            .WithName("DetectTrips")
            .WithDescription("Re-runs trip detection for every active user");
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] SettingsService settings,
        [FromServices] ReverseGeocoder geocoder,
        [FromServices] IServiceScopeFactory scopeFactory,
        CancellationToken ct)
    {
        if (!geocoder.IsAvailable)
            return Results.BadRequest(new
            {
                message = "No hay datos de lugares disponibles, así que los viajes no tendrían nombre. Revisa Geo:CitiesPath.",
            });

        var tz = await MetadataTimeZone.ResolveAsync(settings, ct);
        var localToday = MetadataTimeZone.LocalNow(tz);

        var userIds = await db.Users
            .Where(u => u.IsActive)
            .Select(u => u.Id)
            .ToListAsync(ct);

        int created = 0, updated = 0, removed = 0, withoutHome = 0, failed = 0;

        foreach (var userId in userIds)
        {
            try
            {
                using var scope = scopeFactory.CreateScope();
                var detection = scope.ServiceProvider.GetRequiredService<TripDetectionService>();

                var result = await detection.RunForUserAsync(userId, localToday, ct);
                created += result.Created;
                updated += result.Updated;
                removed += result.Removed;
                if (!result.HomeFound) withoutHome++;
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                failed++;
                Console.WriteLine($"[TRIPS] Detection failed for user {userId}: {ex.Message}");
            }
        }

        return Results.Ok(new
        {
            users = userIds.Count,
            created,
            updated,
            removed,
            withoutHome,
            failed,
            message = $"Procesados {userIds.Count} usuario(s): {created} viajes nuevos, {updated} actualizados, {removed} retirados, {withoutHome} sin casa detectada, {failed} error(es).",
        });
    }
}
