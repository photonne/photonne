using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;
using Photonne.Client.Web.Models;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Maintenance;

public class MaintenanceTaskResult
{
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
    public int Processed { get; set; }
    public int Affected { get; set; }
}

public class MaintenanceEndpoint : IEndpoint
{
    // camelCase + case-insensitive, matching the ASP.NET response serializer so
    // the buffered JSON we replay via /api/tasks/{id}/stream is wire-identical
    // to the direct stream (the Native client deserializes case-sensitively).
    private static readonly JsonSerializerOptions _jsonOptions = new(JsonSerializerDefaults.Web);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var serviceProvider = app.ServiceProvider;
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Maintenance")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        // ── Legacy synchronous endpoints (kept for the web client / back-compat).
        //    They run the whole task inline and only respond when finished, so on
        //    a large library they can exceed the client's socket idle timeout.
        //    Prefer the streaming variant below. ──────────────────────────────

        group.MapPost("orphan-thumbnails", (MaintenanceService svc, CancellationToken ct) =>
                RunSync(svc, "orphan-thumbnails", false, ct))
            .WithName("CleanOrphanThumbnails")
            .WithDescription("Deletes thumbnail files on disk that have no matching asset in the database.");

        group.MapPost("missing-files", (MaintenanceService svc, CancellationToken ct) =>
                RunSync(svc, "missing-files", false, ct))
            .WithName("MarkMissingFiles")
            .WithDescription("Marks as offline any asset whose physical file no longer exists on disk.");

        group.MapPost("recalculate-sizes", (MaintenanceService svc, CancellationToken ct) =>
                RunSync(svc, "recalculate-sizes", false, ct))
            .WithName("RecalculateSizes")
            .WithDescription("Recalculates the stored file size for every asset by reading the actual file on disk.");

        group.MapPost("empty-trash", (MaintenanceService svc, CancellationToken ct) =>
                RunSync(svc, "empty-trash", false, ct))
            .WithName("EmptyGlobalTrash")
            .WithDescription("Permanently deletes all assets currently in the trash for all users.");

        // dryRun is optional (bool? so a bare POST isn't a 400); absent = purge for real.
        group.MapPost("purge-missing", (MaintenanceService svc, [FromQuery] bool? dryRun, CancellationToken ct) =>
                RunSync(svc, "purge-missing", dryRun ?? false, ct))
            .WithName("PurgeMissingAssets")
            .WithDescription("Permanently deletes assets marked as missing and folder records whose physical directory no longer exists. Pass ?dryRun=true to preview without deleting.");

        // ── Streaming variant: runs on a background task that survives HTTP
        //    disconnection and streams NDJSON progress. Mirrors the pipeline
        //    tasks (index/thumbnails/metadata). ──────────────────────────────

        group.MapGet("{kind}/stream", (
            string kind,
            [FromQuery] bool? dryRun,
            [FromServices] BackgroundTaskManager backgroundTaskManager,
            HttpContext httpContext,
            CancellationToken cancellationToken) =>
            HandleStream(kind, dryRun ?? false, serviceProvider, backgroundTaskManager, httpContext,
                Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var uid) ? uid : Guid.Empty,
                cancellationToken))
            .WithName("MaintenanceTaskStream")
            .WithDescription("Streams progress for a maintenance task (orphan-thumbnails, missing-files, recalculate-sizes, empty-trash, purge-missing) run as a background job.");
    }

    private static async Task<IResult> RunSync(MaintenanceService svc, string kind, bool dryRun, CancellationToken ct)
    {
        var task = svc.Run(kind, dryRun, onProgress: null, ct);
        if (task == null) return Results.NotFound($"Unknown maintenance task '{kind}'.");
        return Results.Ok(await task);
    }

    private Task HandleStream(
        string kind,
        bool dryRun,
        IServiceProvider serviceProvider,
        BackgroundTaskManager backgroundTaskManager,
        HttpContext httpContext,
        Guid userId,
        CancellationToken cancellationToken)
    {
        // One maintenance task at a time (dedup by type). The concrete kind lives
        // in Parameters so the task surfaces in /api/tasks and the client can
        // re-attach. This matches the maintenance screen's UI guard, which blocks
        // every action while one is running.
        var entry = backgroundTaskManager.GetOrCreateRunning(BackgroundTaskType.Maintenance,
            new Dictionary<string, string> { ["kind"] = kind, ["dryRun"] = dryRun.ToString() }, out var created);
        var taskCt = entry.Cts.Token;

        void Send(MaintenanceProgressUpdate upd)
        {
            upd.TaskId = entry.Id;
            entry.Push(JsonSerializer.Serialize(upd, _jsonOptions), upd.Percentage, upd.Message);
        }

        if (created)
        {
            // Immediate first event carrying the TaskId, before the (potentially
            // slow) table scan, so the task surfaces instantly instead of after
            // the scan — the client's fire-and-forget trigger otherwise times out
            // on a silent 0 %.
            Send(new MaintenanceProgressUpdate { Message = "Iniciando…", Percentage = 0 });

            _ = Task.Run(async () =>
            {
                try
                {
                    using var scope = serviceProvider.CreateScope();
                    var svc = scope.ServiceProvider.GetRequiredService<MaintenanceService>();
                    var notificationService = scope.ServiceProvider.GetRequiredService<INotificationService>();

                    var task = svc.Run(kind, dryRun, Send, taskCt);
                    if (task == null)
                    {
                        Send(new MaintenanceProgressUpdate
                        {
                            Message = $"Tarea de mantenimiento desconocida: '{kind}'.",
                            IsCompleted = true
                        });
                        entry.Finish("Failed");
                        return;
                    }

                    var result = await task;

                    Send(new MaintenanceProgressUpdate
                    {
                        Message = result.Message,
                        Percentage = 100,
                        Processed = result.Processed,
                        Affected = result.Affected,
                        IsCompleted = true
                    });
                    entry.Finish("Completed");

                    if (userId != Guid.Empty)
                        await notificationService.CreateAsync(userId, NotificationType.JobCompleted,
                            "Mantenimiento completado", result.Message);
                }
                catch (OperationCanceledException)
                {
                    Send(new MaintenanceProgressUpdate { Message = "Proceso cancelado.", IsCompleted = true });
                    entry.Finish("Cancelled");
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[MAINTENANCE] Error fatal en '{kind}': {ex}");
                    Send(new MaintenanceProgressUpdate { Message = $"Error: {ex.Message}", IsCompleted = true });
                    entry.Finish("Failed");

                    if (userId != Guid.Empty)
                    {
                        try
                        {
                            using var notifyScope = serviceProvider.CreateScope();
                            var notifySvc = notifyScope.ServiceProvider.GetRequiredService<INotificationService>();
                            var reason = ex.Message.Length > 200 ? ex.Message[..200] + "…" : ex.Message;
                            await notifySvc.CreateAsync(userId, NotificationType.JobFailed,
                                "Mantenimiento fallido",
                                $"La tarea se ha interrumpido: {reason}");
                        }
                        catch { /* best effort */ }
                    }
                }
            }, taskCt);
        }

        // Single streaming path for both the creating request and any late
        // subscribers: replay buffered updates from the start, then live ones
        // until the worker calls Finish(). Written as NDJSON (one object per line,
        // flushed) so the Native line-reader gets live progress. Dropping this
        // connection (user leaves the screen) never touches taskCt, so the worker
        // runs on.
        return BackgroundTaskStreaming.WriteNdjsonAsync(httpContext, entry, cancellationToken);
    }
}
