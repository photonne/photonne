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

namespace Photonne.Server.Api.Features.Metadata;

/// <summary>
/// Bulk "restore capture dates from EXIF" task. Recomputes each asset's
/// timeline date (<see cref="Asset.CapturedAt"/>) from its EXIF
/// DateTimeOriginal. Two modes:
///   • fast (default): uses the EXIF already stored in the database — no disk I/O;
///   • fromFile=true: re-reads each physical file's EXIF first (for assets indexed
///     before metadata extraction ran).
/// Reuses the streaming / dedup / notification plumbing of the metadata extractor.
/// </summary>
public class RestoreDatesEndpoint : IEndpoint
{
    private static readonly JsonSerializerOptions _jsonOptions = new(JsonSerializerDefaults.Web);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var serviceProvider = app.ServiceProvider;
        app.MapGet("/api/assets/dates/restore/stream", (
            [FromQuery] bool fromFile,
            [FromServices] BackgroundTaskManager backgroundTaskManager,
            HttpContext httpContext,
            CancellationToken cancellationToken) =>
            HandleStream(fromFile, serviceProvider, backgroundTaskManager,
                Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var uid) ? uid : Guid.Empty,
                cancellationToken))
        .WithName("RestoreDatesStream")
        .WithTags("Assets")
        .WithDescription("Streams date-restoration progress. Use fromFile=true to re-read EXIF from disk first.")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private async IAsyncEnumerable<MetadataProgressUpdate> HandleStream(
        bool fromFile,
        IServiceProvider serviceProvider,
        BackgroundTaskManager backgroundTaskManager,
        Guid userId,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var entry = backgroundTaskManager.GetOrCreateRunning(BackgroundTaskType.DateRestore,
            new Dictionary<string, string> { ["fromFile"] = fromFile.ToString() }, out var created);
        var taskCt = entry.Cts.Token;
        void Send(MetadataProgressUpdate upd)
        {
            upd.TaskId = entry.Id;
            entry.Push(JsonSerializer.Serialize(upd, _jsonOptions), upd.Percentage, upd.Message);
        }

        if (created)
        {
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

                var workersRaw = await settingsService.GetSettingAsync(
                    "TaskSettings.MetadataWorkers", Guid.Empty, "2");
                var workers = Math.Clamp(int.TryParse(workersRaw, out var w) ? w : 2, 1, 32);

                var assets = await dbContext.Assets
                    .Include(a => a.Exif)
                    .Where(a => a.Type == AssetType.Image || a.Type == AssetType.Video)
                    .OrderBy(a => a.FileCreatedAt)
                    .Select(a => new
                    {
                        a.Id,
                        a.FullPath,
                        a.FileName,
                        ExifDate = a.Exif != null ? a.Exif.DateTimeOriginal : null
                    })
                    .ToListAsync(taskCt);

                var totalAssets = assets.Count;
                int processed = 0, updated = 0, skipped = 0, failed = 0;

                MetadataJobStatistics Snapshot() => new()
                {
                    TotalAssets = totalAssets,
                    Processed   = Volatile.Read(ref processed),
                    Extracted   = Volatile.Read(ref updated),
                    Skipped     = Volatile.Read(ref skipped),
                    Failed      = Volatile.Read(ref failed)
                };

                Send(new MetadataProgressUpdate
                {
                    Message = $"Encontrados {totalAssets} assets. Restaurando fechas con {workers} worker(s)...",
                    Percentage = 0,
                    Statistics = Snapshot()
                });

                await Parallel.ForEachAsync(
                    assets,
                    new ParallelOptions { MaxDegreeOfParallelism = workers, CancellationToken = taskCt },
                    async (asset, ct) =>
                    {
                        using var innerScope = serviceProvider.CreateScope();
                        var innerDb       = innerScope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                        var innerExif     = innerScope.ServiceProvider.GetRequiredService<ExifExtractorService>();
                        var innerSettings = innerScope.ServiceProvider.GetRequiredService<SettingsService>();

                        try
                        {
                            DateTime? newDate = asset.ExifDate;
                            bool fileMissing = false;

                            if (fromFile)
                            {
                                var physicalPath = await innerSettings.ResolvePhysicalPathAsync(asset.FullPath);
                                if (!File.Exists(physicalPath))
                                {
                                    fileMissing = true;
                                }
                                else
                                {
                                    var result = await innerExif.ExtractExifAsync(physicalPath, ct);
                                    newDate = result?.DateTimeOriginal;

                                    // Persist the freshly-read date onto the EXIF row so a
                                    // later fast pass (and the detail view) see it too.
                                    if (newDate != null)
                                    {
                                        var exifRow = await innerDb.AssetExifs
                                            .FirstOrDefaultAsync(e => e.AssetId == asset.Id, ct);
                                        if (exifRow == null)
                                        {
                                            exifRow = new AssetExif { AssetId = asset.Id };
                                            innerDb.AssetExifs.Add(exifRow);
                                        }
                                        exifRow.DateTimeOriginal = newDate;
                                    }
                                }
                            }

                            if (fileMissing)
                            {
                                Interlocked.Increment(ref failed);
                            }
                            else if (newDate == null)
                            {
                                Interlocked.Increment(ref skipped);
                            }
                            else
                            {
                                var assetRow = await innerDb.Assets
                                    .FirstOrDefaultAsync(a => a.Id == asset.Id, ct);
                                if (assetRow != null && assetRow.CapturedAt != newDate.Value)
                                {
                                    assetRow.CapturedAt = newDate.Value;
                                    await innerDb.SaveChangesAsync(ct);
                                    Interlocked.Increment(ref updated);
                                }
                                else
                                {
                                    // CapturedAt already correct; still flush any EXIF-row
                                    // change made above in fromFile mode.
                                    if (fromFile) await innerDb.SaveChangesAsync(ct);
                                    Interlocked.Increment(ref skipped);
                                }
                            }
                        }
                        catch (OperationCanceledException) { throw; }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[DATE-RESTORE] Error en asset {asset.Id}: {ex.Message}");
                            Interlocked.Increment(ref failed);
                        }

                        var doneCount = Interlocked.Increment(ref processed);
                        Send(new MetadataProgressUpdate
                        {
                            Message = Volatile.Read(ref failed) > 0
                                ? $"Procesado {doneCount}/{totalAssets} — {Volatile.Read(ref failed)} errores"
                                : $"Procesado {doneCount}/{totalAssets}",
                            Percentage = totalAssets > 0 ? (double)doneCount / totalAssets * 100 : 100,
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
                        "Fechas restauradas", completionMsg);
            }
            catch (OperationCanceledException)
            {
                Send(new MetadataProgressUpdate { Message = "Proceso cancelado.", IsCompleted = true });
                entry.Finish("Cancelled");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DATE-RESTORE] Error fatal: {ex.Message}");
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
                            "Restauración de fechas fallida",
                            $"La tarea se ha interrumpido: {reason}");
                    }
                    catch { /* best effort */ }
                }
            }
            }, taskCt);
        }

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
            parts.Add($"{stats.Extracted} actualizados");
        if (stats.Skipped > 0)
            parts.Add($"{stats.Skipped} sin cambios");
        if (stats.Failed > 0)
            parts.Add($"{stats.Failed} fallidos");
        var summary = parts.Count > 0 ? string.Join(", ", parts) : "nada que hacer";
        return $"Completado — {summary}.";
    }
}
