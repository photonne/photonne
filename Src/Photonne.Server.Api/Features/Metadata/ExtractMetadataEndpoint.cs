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
using Photonne.Server.Api.Shared.Dtos;

namespace Photonne.Server.Api.Features.Metadata;

public class ExtractMetadataEndpoint : IEndpoint
{
    // camelCase + case-insensitive, matching the ASP.NET response serializer so
    // the buffered JSON we replay via /api/tasks/{id}/stream is wire-identical
    // to the direct stream (the Native client deserializes case-sensitively).
    private static readonly JsonSerializerOptions _jsonOptions = new(JsonSerializerDefaults.Web);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var serviceProvider = app.ServiceProvider;
        app.MapGet("/api/assets/metadata/stream", (
            [FromQuery] bool overwrite,
            [FromServices] BackgroundTaskManager backgroundTaskManager,
            HttpContext httpContext,
            CancellationToken cancellationToken) =>
            HandleStream(overwrite, serviceProvider, backgroundTaskManager,
                Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var uid) ? uid : Guid.Empty,
                cancellationToken))
        .WithName("ExtractMetadataStream")
        .WithTags("Assets")
        .WithDescription("Streams metadata extraction progress. Use overwrite=true to re-extract all assets.")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private async IAsyncEnumerable<MetadataProgressUpdate> HandleStream(
        bool overwrite,
        IServiceProvider serviceProvider,
        BackgroundTaskManager backgroundTaskManager,
        Guid userId,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        // Dedup: attach to an already-running metadata pass instead of spawning
        // a second worker (e.g. the user re-triggers after navigating away and
        // back). Only the request that actually creates the entry runs the work;
        // every other caller just subscribes to the same buffered stream.
        var entry = backgroundTaskManager.GetOrCreateRunning(BackgroundTaskType.Metadata,
            new Dictionary<string, string> { ["overwrite"] = overwrite.ToString() }, out var created);
        var taskCt = entry.Cts.Token;
        void Send(MetadataProgressUpdate upd)
        {
            upd.TaskId = entry.Id;
            entry.Push(JsonSerializer.Serialize(upd, _jsonOptions), upd.Percentage, upd.Message);
        }

        if (created)
        {
            // Emit an immediate first event carrying the TaskId BEFORE the heavy
            // asset-table scan below — otherwise the task surfaces only after the
            // scan (many seconds on a real library) and the client's fire-and-
            // forget trigger times out on a silent 0%.
            Send(new MetadataProgressUpdate
            {
                Message = "Escaneando biblioteca…",
                Percentage = 0,
                Statistics = new MetadataJobStatistics()
            });

            _ = Task.Run(async () =>
            {
            try
            {
                using var scope = serviceProvider.CreateScope();
                var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();
                var notificationService = scope.ServiceProvider.GetRequiredService<INotificationService>();

                // Worker count (TaskSettings.MetadataWorkers, default 2, clamped to [1..32])
                var workersRaw = await settingsService.GetSettingAsync(
                    "TaskSettings.MetadataWorkers", Guid.Empty, "2");
                var metadataWorkers = Math.Clamp(int.TryParse(workersRaw, out var w) ? w : 2, 1, 32);

                var assets = await dbContext.Assets
                    .Include(a => a.Exif)
                    .Where(a => a.Type == AssetType.Image || a.Type == AssetType.Video)
                    .OrderBy(a => a.FileCreatedAt)
                    .Select(a => new
                    {
                        a.Id, a.FullPath, a.FileName, a.Type, a.CapturedAt,
                        ExifDate = a.Exif != null ? a.Exif.DateTimeOriginal : null
                    })
                    .ToListAsync(taskCt);

                var totalAssets = assets.Count;
                int processed = 0, extracted = 0, skipped = 0, failed = 0;

                MetadataJobStatistics Snapshot() => new()
                {
                    TotalAssets = totalAssets,
                    Processed   = Volatile.Read(ref processed),
                    Extracted   = Volatile.Read(ref extracted),
                    Skipped     = Volatile.Read(ref skipped),
                    Failed      = Volatile.Read(ref failed)
                };

                Send(new MetadataProgressUpdate
                {
                    Message = $"Encontrados {totalAssets} assets con soporte de metadatos. Iniciando con {metadataWorkers} worker(s)...",
                    Percentage = 0,
                    Statistics = Snapshot()
                });

                await Parallel.ForEachAsync(
                    assets,
                    new ParallelOptions { MaxDegreeOfParallelism = metadataWorkers, CancellationToken = taskCt },
                    async (asset, ct) =>
                    {
                        // Each parallel task gets its own DI scope so DbContext / services
                        // are not shared across threads.
                        using var innerScope = serviceProvider.CreateScope();
                        var innerDb       = innerScope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                        var innerExif     = innerScope.ServiceProvider.GetRequiredService<ExifExtractorService>();
                        var innerSettings = innerScope.ServiceProvider.GetRequiredService<SettingsService>();

                        try
                        {
                            if (!overwrite && asset.ExifDate != null)
                            {
                                // Skipping re-extraction, but still re-sync the
                                // timeline date with the EXIF already stored —
                                // a re-index can re-seed CapturedAt from a
                                // polluted filesystem date AFTER the EXIF was
                                // extracted, and the skip path used to leave
                                // that divergence in place forever.
                                if (asset.CapturedAt != asset.ExifDate.Value)
                                {
                                    var skippedRow = await innerDb.Assets
                                        .FirstOrDefaultAsync(a => a.Id == asset.Id, ct);
                                    if (skippedRow != null)
                                    {
                                        skippedRow.CapturedAt = asset.ExifDate.Value;
                                        await innerDb.SaveChangesAsync(ct);
                                    }
                                }
                                Interlocked.Increment(ref skipped);
                            }
                            else
                            {
                                Send(new MetadataProgressUpdate
                                {
                                    Message = $"Extrayendo: {asset.FileName}",
                                    Percentage = totalAssets > 0 ? (double)Volatile.Read(ref processed) / totalAssets * 100 : 0,
                                    Statistics = Snapshot()
                                });

                                var physicalPath = await innerSettings.ResolvePhysicalPathAsync(asset.FullPath);

                                if (!File.Exists(physicalPath))
                                {
                                    Interlocked.Increment(ref failed);
                                }
                                else
                                {
                                    var result = await innerExif.ExtractExifAsync(physicalPath, ct);
                                    if (result != null)
                                    {
                                        result.AssetId = asset.Id;

                                        var existing = await innerDb.AssetExifs
                                            .FirstOrDefaultAsync(e => e.AssetId == asset.Id, ct);
                                        if (existing != null)
                                            innerDb.AssetExifs.Remove(existing);

                                        innerDb.AssetExifs.Add(result);

                                        // Keep CapturedAt in sync with the freshly-extracted EXIF
                                        // so the admin re-extraction can fix timeline ordering on
                                        // assets that were indexed before EXIF was processed (or
                                        // before CapturedAt existed at all). Fall back to the
                                        // effective filesystem timestamp (min of created/modified)
                                        // when DateTimeOriginal is absent.
                                        var assetRow = await innerDb.Assets
                                            .FirstOrDefaultAsync(a => a.Id == asset.Id, ct);
                                        if (assetRow != null)
                                        {
                                            assetRow.CapturedAt = result.DateTimeOriginal ?? assetRow.EffectiveFileCreatedAt;
                                        }

                                        await innerDb.SaveChangesAsync(ct);

                                        Interlocked.Increment(ref extracted);
                                    }
                                    else
                                    {
                                        Interlocked.Increment(ref failed);
                                    }
                                }
                            }
                        }
                        catch (OperationCanceledException) { throw; }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[METADATA] Error procesando asset {asset.Id}: {ex.Message}");
                            Interlocked.Increment(ref failed);
                        }

                        var done = Interlocked.Increment(ref processed);
                        Send(new MetadataProgressUpdate
                        {
                            Message = Volatile.Read(ref failed) > 0
                                ? $"Procesado {done}/{totalAssets} — {Volatile.Read(ref failed)} errores"
                                : $"Procesado {done}/{totalAssets}",
                            Percentage = totalAssets > 0 ? (double)done / totalAssets * 100 : 100,
                            Statistics = Snapshot()
                        });
                    });

                var finalStats = Snapshot();
                var completionMsg = BuildCompletionMessage(finalStats);
                Send(new MetadataProgressUpdate
                {
                    Message = completionMsg,
                    Percentage = 100,
                    Statistics = finalStats,
                    IsCompleted = true
                });

                entry.Finish("Completed");

                if (userId != Guid.Empty)
                    await notificationService.CreateAsync(userId, NotificationType.JobCompleted,
                        "Metadatos extraídos", completionMsg);
            }
            catch (OperationCanceledException)
            {
                Send(new MetadataProgressUpdate { Message = "Proceso cancelado.", IsCompleted = true });
                entry.Finish("Cancelled");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[METADATA] Error fatal: {ex.Message}");
                Send(new MetadataProgressUpdate { Message = $"Error: {ex.Message}", IsCompleted = true });
                entry.Finish("Failed");

                if (userId != Guid.Empty)
                {
                    try
                    {
                        using var notifyScope = serviceProvider.CreateScope();
                        var notifySvc = notifyScope.ServiceProvider.GetRequiredService<INotificationService>();
                        var reason = ex.Message.Length > 200 ? ex.Message[..200] + "…" : ex.Message;
                        await notifySvc.CreateAsync(userId, NotificationType.JobFailed,
                            "Extracción de metadatos fallida",
                            $"La tarea se ha interrumpido: {reason}");
                    }
                    catch { /* best effort */ }
                }
            }
            }, taskCt);
        }

        // Single streaming path for both the creating request and any late
        // subscribers: replay the buffered updates from the start, then live
        // ones until the worker calls Finish(). Dropping this connection (user
        // leaves the screen) never touches taskCt, so the worker runs on.
        await foreach (var json in entry.StreamAsync(0, cancellationToken))
        {
            MetadataProgressUpdate? upd;
            try { upd = JsonSerializer.Deserialize<MetadataProgressUpdate>(json, _jsonOptions); }
            catch { continue; }
            if (upd != null) yield return upd;
        }
    }

    private static string BuildCompletionMessage(MetadataJobStatistics stats)
    {
        var parts = new List<string>();
        if (stats.Extracted > 0)
            parts.Add($"{stats.Extracted} extraídos");
        if (stats.Skipped > 0)
            parts.Add($"{stats.Skipped} omitidos");
        if (stats.Failed > 0)
            parts.Add($"{stats.Failed} fallidos");
        var summary = parts.Count > 0 ? string.Join(", ", parts) : "nada que hacer";
        return $"Completado — {summary}.";
    }

}
