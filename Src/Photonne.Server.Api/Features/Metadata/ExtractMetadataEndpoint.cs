using System.Runtime.CompilerServices;
using System.Security.Claims;
using System.Text.Json;
using System.Threading.Channels;
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
        var channel = Channel.CreateUnbounded<MetadataProgressUpdate>();

        var entry = backgroundTaskManager.Register(BackgroundTaskType.Metadata,
            new Dictionary<string, string> { ["overwrite"] = overwrite.ToString() });
        var taskCt = entry.Cts.Token;
        void Send(MetadataProgressUpdate upd)
        {
            upd.TaskId = entry.Id;
            channel.Writer.TryWrite(upd);
            entry.Push(JsonSerializer.Serialize(upd), upd.Percentage, upd.Message);
        }

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
                    .Select(a => new { a.Id, a.FullPath, a.FileName, a.Type, HasExif = a.Exif != null && a.Exif.DateTimeOriginal != null })
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
                            if (!overwrite && asset.HasExif)
                            {
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
            }
            finally
            {
                channel.Writer.TryComplete();
            }
        }, taskCt);

        await foreach (var update in channel.Reader.ReadAllAsync(cancellationToken))
        {
            yield return update;
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
