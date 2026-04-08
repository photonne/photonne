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
                var exifService = scope.ServiceProvider.GetRequiredService<ExifExtractorService>();
                var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();
                var notificationService = scope.ServiceProvider.GetRequiredService<INotificationService>();

                var assets = await dbContext.Assets
                    .Include(a => a.Exif)
                    .Where(a => a.Type == AssetType.Image || a.Type == AssetType.Video)
                    .OrderBy(a => a.FileCreatedAt)
                    .Select(a => new { a.Id, a.FullPath, a.FileName, a.Type, HasExif = a.Exif != null && a.Exif.DateTimeOriginal != null })
                    .ToListAsync(taskCt);

                var stats = new MetadataJobStatistics { TotalAssets = assets.Count };

                Send(new MetadataProgressUpdate
                {
                    Message = $"Encontrados {assets.Count} assets con soporte de metadatos. Iniciando...",
                    Percentage = 0,
                    Statistics = Clone(stats)
                });

                foreach (var asset in assets)
                {
                    if (taskCt.IsCancellationRequested) break;

                    try
                    {
                        if (!overwrite && asset.HasExif)
                        {
                            stats.Skipped++;
                        }
                        else
                        {
                            Send(new MetadataProgressUpdate
                            {
                                Message = $"Extrayendo: {asset.FileName}",
                                Percentage = stats.TotalAssets > 0 ? (double)stats.Processed / stats.TotalAssets * 100 : 0,
                                Statistics = Clone(stats)
                            });

                            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);

                            if (!File.Exists(physicalPath))
                            {
                                stats.Failed++;
                            }
                            else
                            {
                                var extracted = await exifService.ExtractExifAsync(physicalPath, taskCt);

                                if (extracted != null)
                                {
                                    extracted.AssetId = asset.Id;

                                    // Remove stale record and replace with fresh extraction
                                    var existing = await dbContext.AssetExifs
                                        .FirstOrDefaultAsync(e => e.AssetId == asset.Id, taskCt);
                                    if (existing != null)
                                        dbContext.AssetExifs.Remove(existing);

                                    dbContext.AssetExifs.Add(extracted);
                                    await dbContext.SaveChangesAsync(taskCt);

                                    stats.Extracted++;
                                }
                                else
                                {
                                    stats.Failed++;
                                }
                            }
                        }
                    }
                    catch (OperationCanceledException) { throw; }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[METADATA] Error procesando asset {asset.Id}: {ex.Message}");
                        stats.Failed++;
                    }

                    stats.Processed++;

                    Send(new MetadataProgressUpdate
                    {
                        Message = stats.Failed > 0
                            ? $"Procesado {stats.Processed}/{stats.TotalAssets} — {stats.Failed} errores"
                            : $"Procesado {stats.Processed}/{stats.TotalAssets}",
                        Percentage = stats.TotalAssets > 0 ? (double)stats.Processed / stats.TotalAssets * 100 : 100,
                        Statistics = Clone(stats)
                    });
                }

                var completionMsg = BuildCompletionMessage(stats);
                Send(new MetadataProgressUpdate
                {
                    Message = completionMsg,
                    Percentage = 100,
                    Statistics = Clone(stats),
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

    private static MetadataJobStatistics Clone(MetadataJobStatistics s) => new()
    {
        TotalAssets = s.TotalAssets,
        Processed = s.Processed,
        Extracted = s.Extracted,
        Skipped = s.Skipped,
        Failed = s.Failed
    };
}
