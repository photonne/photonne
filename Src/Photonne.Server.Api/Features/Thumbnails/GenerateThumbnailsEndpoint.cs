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

namespace Photonne.Server.Api.Features.Thumbnails;

public class GenerateThumbnailsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var serviceProvider = app.ServiceProvider;
        app.MapGet("/api/assets/thumbnails/stream", (
            [FromQuery] bool regenerate,
            [FromServices] BackgroundTaskManager backgroundTaskManager,
            HttpContext httpContext,
            CancellationToken cancellationToken) =>
            HandleStream(regenerate, serviceProvider, backgroundTaskManager,
                Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var uid) ? uid : Guid.Empty,
                cancellationToken))
        .WithName("GenerateThumbnailsStream")
        .WithTags("Assets")
        .WithDescription("Streams thumbnail generation progress. Use regenerate=true to force-regenerate all thumbnails.")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private async IAsyncEnumerable<ThumbnailProgressUpdate> HandleStream(
        bool regenerate,
        IServiceProvider serviceProvider,
        BackgroundTaskManager backgroundTaskManager,
        Guid userId,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var channel = Channel.CreateUnbounded<ThumbnailProgressUpdate>();

        var entry = backgroundTaskManager.Register(BackgroundTaskType.Thumbnails,
            new Dictionary<string, string> { ["regenerate"] = regenerate.ToString() });
        var taskCt = entry.Cts.Token;
        void Send(ThumbnailProgressUpdate upd)
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
                var thumbnailService = scope.ServiceProvider.GetRequiredService<ThumbnailGeneratorService>();
                var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();
                var notificationService = scope.ServiceProvider.GetRequiredService<INotificationService>();

                // Reload thumbnails path in case it was changed in server settings
                await thumbnailService.RefreshThumbnailsPathAsync();

                var assets = await dbContext.Assets
                    .OrderBy(a => a.FileCreatedAt)
                    .Select(a => new { a.Id, a.FullPath, a.FileName })
                    .ToListAsync(taskCt);

                var stats = new ThumbnailJobStatistics { TotalAssets = assets.Count };

                Send(new ThumbnailProgressUpdate
                {
                    Message = $"Encontrados {assets.Count} assets. Iniciando...",
                    Percentage = 0,
                    Statistics = Clone(stats)
                });

                foreach (var asset in assets)
                {
                    if (taskCt.IsCancellationRequested) break;

                    try
                    {
                        var missingSizes = thumbnailService.GetMissingThumbnailSizes(asset.Id);
                        bool needsGeneration = regenerate || missingSizes.Count > 0;

                        if (!needsGeneration)
                        {
                            stats.Skipped++;
                        }
                        else
                        {
                            Send(new ThumbnailProgressUpdate
                            {
                                Message = $"Procesando: {asset.FileName}",
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
                                var generated = await thumbnailService.GenerateThumbnailsAsync(
                                    physicalPath, asset.Id, taskCt);

                                if (generated.Count > 0)
                                {
                                    // Remove stale DB records and replace with fresh ones
                                    var existing = await dbContext.AssetThumbnails
                                        .Where(t => t.AssetId == asset.Id)
                                        .ToListAsync(taskCt);
                                    dbContext.AssetThumbnails.RemoveRange(existing);
                                    dbContext.AssetThumbnails.AddRange(generated);
                                    await dbContext.SaveChangesAsync(taskCt);

                                    stats.Generated += generated.Count;
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
                        Console.WriteLine($"[THUMBNAILS] Error procesando asset {asset.Id}: {ex.Message}");
                        stats.Failed++;
                    }

                    stats.Processed++;

                    // Emit progress every asset to keep UI responsive
                    Send(new ThumbnailProgressUpdate
                    {
                        Message = stats.Failed > 0
                            ? $"Procesado {stats.Processed}/{stats.TotalAssets} — {stats.Failed} errores"
                            : $"Procesado {stats.Processed}/{stats.TotalAssets}",
                        Percentage = stats.TotalAssets > 0 ? (double)stats.Processed / stats.TotalAssets * 100 : 100,
                        Statistics = Clone(stats)
                    });
                }

                var completionMsg = BuildCompletionMessage(stats, regenerate);
                Send(new ThumbnailProgressUpdate
                {
                    Message = completionMsg,
                    Percentage = 100,
                    Statistics = Clone(stats),
                    IsCompleted = true
                });

                entry.Finish("Completed");

                if (userId != Guid.Empty)
                    await notificationService.CreateAsync(userId, NotificationType.JobCompleted,
                        "Miniaturas generadas", completionMsg);
            }
            catch (OperationCanceledException)
            {
                Send(new ThumbnailProgressUpdate { Message = "Proceso cancelado.", IsCompleted = true });
                entry.Finish("Cancelled");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[THUMBNAILS] Error fatal: {ex.Message}");
                Send(new ThumbnailProgressUpdate { Message = $"Error: {ex.Message}", IsCompleted = true });
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

    private static string BuildCompletionMessage(ThumbnailJobStatistics stats, bool regenerate)
    {
        var parts = new List<string>();
        if (stats.Generated > 0)
            parts.Add($"{stats.Generated} generadas");
        if (stats.Skipped > 0)
            parts.Add($"{stats.Skipped} omitidas");
        if (stats.Failed > 0)
            parts.Add($"{stats.Failed} fallidas");
        var summary = parts.Count > 0 ? string.Join(", ", parts) : "nada que hacer";
        return $"Completado — {summary}.";
    }

    private static ThumbnailJobStatistics Clone(ThumbnailJobStatistics s) => new()
    {
        TotalAssets = s.TotalAssets,
        Processed = s.Processed,
        Generated = s.Generated,
        Skipped = s.Skipped,
        Failed = s.Failed
    };
}
