using Microsoft.EntityFrameworkCore;
using Photonne.Client.Web.Models;
using Photonne.Server.Api.Features.Memories.Generation;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Features.Maintenance;

/// <summary>
/// The memories/places half of the maintenance catalogue: the same four passes
/// NightlySchedulerService runs at 03:00, exposed so an admin can run one now.
///
/// They live here rather than as POST endpoints of their own so they inherit
/// what every maintenance task already has: NDJSON progress, cancellation, and a
/// task entry that survives the HTTP connection dropping. A synchronous POST
/// over 100k assets is the shape that used to blow the client's socket timeout.
/// </summary>
public partial class MaintenanceService
{
    /// <summary>Batch used to report geocoding progress. The runner's own batch is
    /// 500 too; matching it means one progress line per database round-trip.</summary>
    private const int GeocodeReportBatch = 500;

    // ─── Interpolate locations ────────────────────────────────────────────────

    private async Task<MaintenanceTaskResult> InterpolateLocationsAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        var runner = new LocationInterpolationRunner(_dbContext);

        var candidates = await runner.CandidateCountAsync(ct);
        if (candidates == 0)
        {
            Report(onProgress, "No hay fotos sin ubicación que rellenar.", 100, 0, 0);
            return new MaintenanceTaskResult
            {
                Success = true,
                Message = "No hay fotos sin ubicación que rellenar.",
                Processed = 0,
                Affected = 0,
            };
        }

        // No percentage: the runner sweeps each owner's timeline in one pass and
        // has no per-item hook. Inventing a bar that jumps 0 → 100 would say less
        // than a sentence that's true.
        Report(onProgress, $"Analizando {candidates} foto(s) sin ubicación…", 0, 0, 0);

        var result = await runner.RunAsync(ct);

        var message = $"Inferidas {result.Filled} ubicación(es) a partir de fotos tomadas al mismo tiempo. " +
                      $"{result.Skipped} se han quedado sin ubicación por falta de referencias fiables.";
        Report(onProgress, message, 100, candidates, result.Filled);

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = candidates,
            Affected = result.Filled,
        };
    }

    // ─── Reverse geocode ──────────────────────────────────────────────────────

    private async Task<MaintenanceTaskResult> ReverseGeocodeAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        using var scope = _scopeFactory.CreateScope();
        var geocoder = scope.ServiceProvider.GetRequiredService<ReverseGeocoder>();

        if (!geocoder.IsAvailable)
        {
            const string missing = "No hay datos de lugares disponibles. Revisa Geo:CitiesPath.";
            Report(onProgress, missing, 100, 0, 0);
            return new MaintenanceTaskResult { Success = false, Message = missing };
        }

        var runner = scope.ServiceProvider.GetRequiredService<GeocodeBackfillRunner>();
        var total = await runner.PendingCountAsync(ct);
        if (total == 0)
        {
            Report(onProgress, "Todas las fotos con coordenadas ya tienen lugar.", 100, 0, 0);
            return new MaintenanceTaskResult
            {
                Success = true,
                Message = "Todas las fotos con coordenadas ya tienen lugar.",
                Processed = 0,
                Affected = 0,
            };
        }

        int processed = 0, matched = 0;
        while (!ct.IsCancellationRequested)
        {
            var batch = await runner.RunAsync(max: GeocodeReportBatch, ct);
            if (batch.Processed == 0) break;

            processed += batch.Processed;
            matched += batch.Matched;

            var percentage = total > 0 ? Math.Min(100.0, processed * 100.0 / total) : 100.0;
            Report(onProgress, $"Geocodificando {processed} / {total}…", percentage, processed, matched);

            if (batch.Pending == 0) break;
        }

        // "Sin lugar" is not a failure: a coordinate in the middle of the sea, or
        // 100 km from the nearest town, honestly has no city to name it.
        var message = $"Geocodificados {processed} asset(s), {matched} con lugar reconocido, " +
                      $"{processed - matched} sin ninguna población cerca.";
        Report(onProgress, message, 100, processed, matched);

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = matched,
        };
    }

    // ─── Detect trips ─────────────────────────────────────────────────────────

    private async Task<MaintenanceTaskResult> DetectTripsAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        using var probe = _scopeFactory.CreateScope();
        var geocoder = probe.ServiceProvider.GetRequiredService<ReverseGeocoder>();

        // Trips would detect fine without place names, but every one of them
        // would be titled "Viaje de julio de 2019". Say so instead of producing
        // a list of anonymous trips the admin then has to explain to themselves.
        if (!geocoder.IsAvailable)
        {
            const string missing = "No hay datos de lugares, así que los viajes no tendrían nombre. Revisa Geo:CitiesPath.";
            Report(onProgress, missing, 100, 0, 0);
            return new MaintenanceTaskResult { Success = false, Message = missing };
        }

        var tz = await MetadataTimeZone.ResolveAsync(_settingsService, ct);
        var localToday = MetadataTimeZone.LocalNow(tz);

        var userIds = await _dbContext.Users.Where(u => u.IsActive).Select(u => u.Id).ToListAsync(ct);

        int created = 0, updated = 0, removed = 0, withoutHome = 0, done = 0;
        foreach (var userId in userIds)
        {
            ct.ThrowIfCancellationRequested();

            using var scope = _scopeFactory.CreateScope();
            var detection = scope.ServiceProvider.GetRequiredService<TripDetectionService>();

            var result = await detection.RunForUserAsync(userId, localToday, ct);
            created += result.Created;
            updated += result.Updated;
            removed += result.Removed;
            if (!result.HomeFound) withoutHome++;

            done++;
            Report(onProgress, $"Analizando usuarios: {done} / {userIds.Count}…",
                done * 100.0 / Math.Max(1, userIds.Count), done, created);
        }

        var message = $"{created} viaje(s) nuevo(s), {updated} actualizado(s), {removed} retirado(s).";
        // Worth surfacing: no home means no trips at all for that user, and the
        // reason is a threshold, not a bug.
        if (withoutHome > 0)
            message += $" {withoutHome} usuario(s) sin casa detectada (hacen falta fotos con GPS en ~60 días distintos).";

        Report(onProgress, message, 100, userIds.Count, created);

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = userIds.Count,
            Affected = created + updated,
        };
    }

    // ─── Generate memories ────────────────────────────────────────────────────

    private async Task<MaintenanceTaskResult> GenerateMemoriesAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        var userIds = await _dbContext.Users.Where(u => u.IsActive).Select(u => u.Id).ToListAsync(ct);

        int created = 0, updated = 0, removed = 0, failed = 0, done = 0;
        foreach (var userId in userIds)
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                using var scope = _scopeFactory.CreateScope();
                var generation = scope.ServiceProvider.GetRequiredService<MemoryGenerationService>();

                var result = await generation.RunForUserAsync(userId, ct);
                created += result.Created;
                updated += result.Updated;
                removed += result.Removed;
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                // One user's broken library must not cost everyone else theirs.
                failed++;
                Console.WriteLine($"[MEMORIES] Rebuild failed for user {userId}: {ex.Message}");
            }

            done++;
            Report(onProgress, $"Generando recuerdos: {done} / {userIds.Count} usuario(s)…",
                done * 100.0 / Math.Max(1, userIds.Count), done, created);
        }

        var message = $"{created} recuerdo(s) nuevo(s), {updated} actualizado(s), {removed} retirado(s).";
        if (failed > 0) message += $" {failed} usuario(s) con error.";

        Report(onProgress, message, 100, userIds.Count, created);

        return new MaintenanceTaskResult
        {
            Success = failed == 0,
            Message = message,
            Processed = userIds.Count,
            Affected = created + updated,
        };
    }
}
