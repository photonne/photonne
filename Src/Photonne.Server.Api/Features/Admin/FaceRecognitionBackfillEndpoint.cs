using System.Runtime.CompilerServices;
using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Client.Web.Models;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Features.Admin;

public record BackfillRequest(int? BatchSize, bool? OnlyMissing);

public record BackfillResponse(int Enqueued, int Total);

public record GlobalReclusterResponse(int OwnersProcessed, int PersonsCreated);

/// <summary>Admin-only: enqueues FaceRecognition ML jobs for all existing image
/// assets that haven't been processed yet. The EnrichmentWorker picks them
/// up at its normal cadence; deduplication in EnrichmentService prevents duplicate
/// pending jobs.</summary>
public class FaceRecognitionBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/face-recognition/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IEnrichmentService mlJobs,
            [FromServices] SettingsService settings,
            [FromServices] INotificationService notifications,
            [FromBody] BackfillRequest? body,
            HttpContext http,
            CancellationToken ct) => MlBackfillRunner.RunAsync(db, mlJobs, settings, AssetEnrichmentType.FaceRecognition, body, ct, notifications: notifications, triggeredBy: AdminEndpointHelpers.GetUserId(http)));

        group.MapGet("/face-recognition/pending-count", (
            [FromServices] ApplicationDbContext db,
            CancellationToken ct) => MlBackfillRunner.GetPendingCountAsync(db, AssetEnrichmentType.FaceRecognition, ct));
    }
}

/// <summary>Admin-only: re-runs the batch face clustering across every owner
/// that has orphan faces. Useful after a backfill so newly-detected faces
/// consolidate into Persons without waiting for the nightly job.</summary>
public class FaceClusteringRunGlobalEndpoint : IEndpoint
{
    // camelCase + case-insensitive, matching the ASP.NET response serializer so
    // the buffered JSON we replay via /api/tasks/{id}/stream is wire-identical
    // to the direct stream (the Native client deserializes case-sensitively).
    private static readonly JsonSerializerOptions _jsonOptions = new(JsonSerializerDefaults.Web);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var serviceProvider = app.ServiceProvider;
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        // Legacy synchronous endpoint (kept for the web client / back-compat).
        group.MapPost("/face-clustering/run", Handle);

        // Streaming variant: runs on a background task that survives HTTP
        // disconnection and streams NDJSON progress. Mirrors the maintenance
        // and pipeline tasks.
        group.MapGet("/face-clustering/stream", (
            [FromServices] BackgroundTaskManager backgroundTaskManager,
            HttpContext http,
            CancellationToken cancellationToken) =>
            StreamClustering(serviceProvider, backgroundTaskManager,
                AdminEndpointHelpers.GetUserId(http), cancellationToken))
            .WithName("FaceClusteringStream")
            .WithDescription("Streams progress for the global face re-clustering pass, run as a background job.");
    }

    private async IAsyncEnumerable<MaintenanceProgressUpdate> StreamClustering(
        IServiceProvider serviceProvider,
        BackgroundTaskManager backgroundTaskManager,
        Guid userId,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var entry = backgroundTaskManager.GetOrCreateRunning(BackgroundTaskType.FaceClustering, null, out var created);
        var taskCt = entry.Cts.Token;

        void Send(MaintenanceProgressUpdate upd)
        {
            upd.TaskId = entry.Id;
            entry.Push(JsonSerializer.Serialize(upd, _jsonOptions), upd.Percentage, upd.Message);
        }

        if (created)
        {
            Send(new MaintenanceProgressUpdate { Message = "Iniciando…", Percentage = 0 });

            _ = Task.Run(async () =>
            {
                try
                {
                    using var scope = serviceProvider.CreateScope();
                    var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                    var clustering = scope.ServiceProvider.GetRequiredService<FaceClusteringService>();
                    var notifications = scope.ServiceProvider.GetRequiredService<INotificationService>();

                    // Identity is per-user (UserFaceAssignment) since the split. We
                    // interpret the global pass as "for every user that owns assets
                    // with detections, run their clustering once".
                    var ownerIds = (await db.Faces.AsNoTracking()
                        .Select(f => f.Asset.OwnerId)
                        .Where(id => id != null)
                        .Distinct()
                        .ToListAsync(taskCt))
                        .OfType<Guid>()
                        .ToList();

                    var total = ownerIds.Count;
                    var ownersProcessed = 0;
                    var personsCreated = 0;

                    Send(new MaintenanceProgressUpdate
                    {
                        Message = $"Reagrupando rostros de {total} usuario(s)…",
                        Percentage = 0,
                        Processed = 0,
                        Affected = 0
                    });

                    foreach (var oid in ownerIds)
                    {
                        if (taskCt.IsCancellationRequested) break;
                        var createdPersons = await clustering.RunForUserAsync(oid, taskCt);
                        personsCreated += createdPersons;
                        ownersProcessed++;

                        Send(new MaintenanceProgressUpdate
                        {
                            Message = $"Procesados {ownersProcessed}/{total} usuario(s) — {personsCreated} persona(s) nuevas",
                            Percentage = total > 0 ? (double)ownersProcessed / total * 100 : 100,
                            Processed = ownersProcessed,
                            Affected = personsCreated
                        });
                    }

                    var summary = $"Procesados {ownersProcessed} usuario(s); {personsCreated} persona(s) nuevas.";
                    Send(new MaintenanceProgressUpdate
                    {
                        Message = summary,
                        Percentage = 100,
                        Processed = ownersProcessed,
                        Affected = personsCreated,
                        IsCompleted = true
                    });
                    entry.Finish("Completed");

                    if (userId != Guid.Empty)
                        await notifications.CreateAsync(userId, NotificationType.JobCompleted,
                            "Reagrupación global de rostros completada", summary);
                }
                catch (OperationCanceledException)
                {
                    Send(new MaintenanceProgressUpdate { Message = "Proceso cancelado.", IsCompleted = true });
                    entry.Finish("Cancelled");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[FACE-CLUSTERING] Error fatal: {ex.Message}");
                    Send(new MaintenanceProgressUpdate { Message = $"Error: {ex.Message}", IsCompleted = true });
                    entry.Finish("Failed");

                    if (userId != Guid.Empty)
                    {
                        try
                        {
                            using var notifyScope = serviceProvider.CreateScope();
                            var notifySvc = notifyScope.ServiceProvider.GetRequiredService<INotificationService>();
                            await notifySvc.CreateAsync(userId, NotificationType.JobFailed,
                                "Reagrupación global de rostros fallida",
                                $"Error durante la ejecución: {AdminEndpointHelpers.Truncate(ex.Message, 200)}");
                        }
                        catch { /* best effort */ }
                    }
                }
            }, taskCt);
        }

        await foreach (var json in entry.StreamAsync(0, cancellationToken))
        {
            MaintenanceProgressUpdate? upd;
            try { upd = JsonSerializer.Deserialize<MaintenanceProgressUpdate>(json, _jsonOptions); }
            catch { continue; }
            if (upd != null) yield return upd;
        }
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] FaceClusteringService clustering,
        [FromServices] INotificationService notifications,
        HttpContext http,
        CancellationToken ct)
    {
        var triggeredBy = AdminEndpointHelpers.GetUserId(http);
        try
        {
            // Identity is per-user (UserFaceAssignment) since the split. The
            // legacy "global recluster" passes face filtering had no per-user
            // notion; we now interpret it as "for every user that owns assets
            // with detections, run their clustering once". Users with shared-
            // only access cluster lazily on /people via EnsureUpToDateForUserAsync,
            // so they don't need a global pass.
            var ownerIds = await db.Faces.AsNoTracking()
                .Select(f => f.Asset.OwnerId)
                .Where(id => id != null)
                .Distinct()
                .ToListAsync(ct);

            var ownersProcessed = 0;
            var personsCreated = 0;
            foreach (var oid in ownerIds.OfType<Guid>())
            {
                var created = await clustering.RunForUserAsync(oid, ct);
                personsCreated += created;
                ownersProcessed++;
            }

            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobCompleted,
                    "Reagrupación global de rostros completada",
                    $"Procesados {ownersProcessed} usuario(s); {personsCreated} persona(s) nuevas.");

            return Results.Ok(new GlobalReclusterResponse(ownersProcessed, personsCreated));
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobFailed,
                    "Reagrupación global de rostros fallida",
                    $"Error durante la ejecución: {AdminEndpointHelpers.Truncate(ex.Message, 200)}");
            throw;
        }
    }
}

internal static class AdminEndpointHelpers
{
    public static Guid GetUserId(HttpContext http)
        => Guid.TryParse(http.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var id) ? id : Guid.Empty;

    public static string Truncate(string s, int max)
        => string.IsNullOrEmpty(s) ? string.Empty : (s.Length <= max ? s : s[..max] + "…");
}
