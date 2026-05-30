using System.Collections.Concurrent;
using System.Runtime.CompilerServices;
using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.IndexAssets;

public class IndexAssetsEndpoint : IEndpoint
{
    // camelCase + case-insensitive, matching the ASP.NET response serializer so
    // the buffered JSON we replay via /api/tasks/{id}/stream is wire-identical
    // to the direct stream (the Native client deserializes case-sensitively).
    private static readonly JsonSerializerOptions _jsonOptions = new(JsonSerializerDefaults.Web);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/index/stream", (
            [FromServices] DirectoryScanner directoryScanner,
            [FromServices] SettingsService settingsService,
            [FromServices] ApplicationDbContext dbContext,
            [FromServices] IServiceScopeFactory scopeFactory,
            [FromServices] BackgroundTaskManager backgroundTaskManager,
            HttpContext httpContext,
            CancellationToken cancellationToken) => HandleStream(
                directoryScanner, settingsService, dbContext, scopeFactory, backgroundTaskManager,
                Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var uid) ? uid : Guid.Empty,
                cancellationToken))
        .WithName("IndexAssetsStream")
        .WithTags("Assets")
        .WithDescription("Streams progress while scanning the internal assets directory")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    // Streams progress for an internal indexing pass.
    //
    // Architecture mirrors ExternalLibraryScanService: one DI scope per file,
    // one SaveChangesAsync per file (via AssetIndexingService.IndexFileAsync),
    // enrichment (EXIF, thumbnails, ML) queued asynchronously.
    //
    // Missing files are marked IsFileMissing=true instead of being deleted —
    // a scan that finds zero files cannot wipe the database.
    private async IAsyncEnumerable<IndexProgressUpdate> HandleStream(
        DirectoryScanner directoryScanner,
        SettingsService settingsService,
        ApplicationDbContext dbContext,
        IServiceScopeFactory scopeFactory,
        BackgroundTaskManager backgroundTaskManager,
        Guid userId,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var indexStartTime = DateTime.UtcNow;
        var stats = new IndexStatistics();

        // Dedup: attach to an already-running index pass instead of spawning a
        // second worker (concurrent scans would both mark missing files). Only
        // the request that creates the entry runs the work; others subscribe to
        // the same buffered stream.
        var entry = backgroundTaskManager.GetOrCreateRunning(BackgroundTaskType.IndexAssets, null, out var created);
        var taskCt = entry.Cts.Token;

        void Send(IndexProgressUpdate upd)
        {
            upd.TaskId = entry.Id;
            entry.Push(JsonSerializer.Serialize(upd, _jsonOptions), upd.Percentage, upd.Message);
        }

        if (created)
        {
            _ = Task.Run(async () =>
            {
            try
            {
                var directoryPath = settingsService.GetAssetsPath();
                Console.WriteLine($"[SCAN] Indexando directorio interno: {directoryPath}");

                if (!Directory.Exists(directoryPath))
                {
                    Send(new IndexProgressUpdate
                    {
                        Message = $"Error: El directorio interno no existe: {directoryPath}",
                        IsCompleted = true,
                        Statistics = stats
                    });
                    entry.Finish("Failed");
                    return;
                }

                Send(new IndexProgressUpdate
                {
                    Message = "Reparando permisos de carpetas huérfanas...",
                    Percentage = 2,
                    Statistics = stats
                });

                using (var fixScope = scopeFactory.CreateScope())
                {
                    var fixDb = fixScope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                    await AssignAdminToOrphanedFoldersAsync(fixDb, taskCt);
                }

                Send(new IndexProgressUpdate
                {
                    Message = "Descubriendo archivos...",
                    Percentage = 5,
                    Statistics = stats
                });

                var scannedFiles = (await directoryScanner.ScanDirectoryAsync(directoryPath, taskCt)).ToList();
                stats.TotalFilesFound = scannedFiles.Count;

                int workers;
                using (var settingsScope = scopeFactory.CreateScope())
                {
                    var s = settingsScope.ServiceProvider.GetRequiredService<SettingsService>();
                    var raw = await s.GetSettingAsync("TaskSettings.ThumbnailWorkers", Guid.Empty, "2");
                    workers = Math.Clamp(int.TryParse(raw, out var n) ? n : 2, 1, 16);
                }

                Send(new IndexProgressUpdate
                {
                    Message = $"Descubiertos {stats.TotalFilesFound} archivos. Indexando con {workers} workers...",
                    Percentage = 10,
                    Statistics = stats
                });

                // Snapshot internal-asset paths BEFORE indexing — used to classify
                // each scanned file as new vs existing, and to detect missing files
                // after the scan.
                var existingPaths = new HashSet<string>(
                    await dbContext.Assets
                        .Where(a => a.DeletedAt == null && a.ExternalLibraryId == null)
                        .Select(a => a.FullPath)
                        .ToListAsync(taskCt),
                    StringComparer.OrdinalIgnoreCase);

                var scannedVirtualPaths = new ConcurrentBag<string>();
                int processed = 0;
                int newCount = 0;
                int existingCount = 0;
                int failedCount = 0;
                int total = scannedFiles.Count;

                await Parallel.ForEachAsync(
                    scannedFiles,
                    new ParallelOptions { MaxDegreeOfParallelism = workers, CancellationToken = taskCt },
                    async (file, innerCt) =>
                    {
                        using var fileScope = scopeFactory.CreateScope();
                        var indexingService = fileScope.ServiceProvider.GetRequiredService<AssetIndexingService>();
                        var scopedSettings = fileScope.ServiceProvider.GetRequiredService<SettingsService>();

                        // Internal mode: pass Guid.Empty; IndexFileAsync resolves
                        // owner from the path's username segment, with primary-admin fallback.
                        var asset = await indexingService.IndexFileAsync(file.FullPath, Guid.Empty, innerCt);
                        if (asset == null)
                        {
                            Interlocked.Increment(ref failedCount);
                            return;
                        }

                        var virtualPath = await scopedSettings.VirtualizePathAsync(file.FullPath);
                        scannedVirtualPaths.Add(virtualPath);

                        if (existingPaths.Contains(virtualPath))
                            Interlocked.Increment(ref existingCount);
                        else
                            Interlocked.Increment(ref newCount);

                        var current = Interlocked.Increment(ref processed);
                        if (current % 10 == 0 || current == total)
                        {
                            stats.NewFiles = newCount;
                            stats.UpdatedFiles = existingCount;
                            Send(new IndexProgressUpdate
                            {
                                Message = $"Indexando {current}/{total}: {file.FileName}",
                                Percentage = 10 + ((double)current / Math.Max(total, 1)) * 80,
                                Statistics = stats
                            });
                        }
                    });

                stats.NewFiles = newCount;
                stats.UpdatedFiles = existingCount;

                Send(new IndexProgressUpdate
                {
                    Message = "Marcando archivos ausentes...",
                    Percentage = 92,
                    Statistics = stats
                });

                var scannedSet = new HashSet<string>(scannedVirtualPaths, StringComparer.OrdinalIgnoreCase);
                var missingPaths = existingPaths.Except(scannedSet).ToList();
                int markedMissing = 0;
                if (missingPaths.Count > 0)
                {
                    using var missingScope = scopeFactory.CreateScope();
                    var missingDb = missingScope.ServiceProvider.GetRequiredService<ApplicationDbContext>();

                    var missingAssets = await missingDb.Assets
                        .Where(a => a.DeletedAt == null
                                 && a.ExternalLibraryId == null
                                 && !a.IsFileMissing
                                 && missingPaths.Contains(a.FullPath))
                        .ToListAsync(taskCt);

                    foreach (var asset in missingAssets)
                    {
                        asset.IsFileMissing = true;
                        markedMissing++;
                    }

                    await missingDb.SaveChangesAsync(taskCt);
                }

                // OrphanedFilesRemoved is reused for the count of assets newly
                // marked as missing — the field name is kept for client compatibility.
                stats.OrphanedFilesRemoved = markedMissing;
                stats.IndexCompletedAt = DateTime.UtcNow;
                stats.IndexDuration = stats.IndexCompletedAt - indexStartTime;

                var summary = failedCount > 0
                    ? $"Indexación completada. {newCount} nuevos, {existingCount} existentes, {markedMissing} ausentes, {failedCount} fallidos."
                    : $"Indexación completada. {newCount} nuevos, {existingCount} existentes, {markedMissing} ausentes.";

                Send(new IndexProgressUpdate
                {
                    Message = summary,
                    Percentage = 100,
                    Statistics = stats,
                    IsCompleted = true
                });

                entry.Finish("Completed");

                if (userId != Guid.Empty)
                {
                    try
                    {
                        using var notifyScope = scopeFactory.CreateScope();
                        var notifySvc = notifyScope.ServiceProvider.GetRequiredService<INotificationService>();
                        await notifySvc.CreateAsync(userId, NotificationType.JobCompleted,
                            "Indexación completada", summary);
                    }
                    catch { /* best effort */ }
                }
            }
            catch (OperationCanceledException)
            {
                Send(new IndexProgressUpdate
                {
                    Message = "Indexación cancelada.",
                    IsCompleted = true,
                    Statistics = stats
                });
                entry.Finish("Cancelled");
            }
            catch (Exception ex)
            {
                Send(new IndexProgressUpdate
                {
                    Message = $"Error: {ex.Message}",
                    IsCompleted = true,
                    Statistics = stats
                });
                entry.Finish("Failed");

                if (userId != Guid.Empty)
                {
                    try
                    {
                        using var notifyScope = scopeFactory.CreateScope();
                        var notifySvc = notifyScope.ServiceProvider.GetRequiredService<INotificationService>();
                        var reason = ex.Message.Length > 200 ? ex.Message[..200] + "…" : ex.Message;
                        await notifySvc.CreateAsync(userId, NotificationType.JobFailed,
                            "Indexación fallida",
                            $"La indexación se ha interrumpido: {reason}");
                    }
                    catch { /* best effort */ }
                }
            }
            }, taskCt);
        }

        // Single streaming path for both the creating request and any late
        // subscribers: replay buffered updates from the start, then live ones
        // until the worker calls Finish(). Dropping this connection (user leaves
        // the screen) never touches taskCt, so the worker runs on.
        await foreach (var json in entry.StreamAsync(0, cancellationToken))
        {
            IndexProgressUpdate? upd;
            try { upd = JsonSerializer.Deserialize<IndexProgressUpdate>(json, _jsonOptions); }
            catch { continue; }
            if (upd != null) yield return upd;
        }
    }

    // Grants the primary admin Read/Write/Delete/ManagePermissions on any
    // non-personal folder that has zero FolderPermission rows. Runs once per
    // scan to recover folders created outside the app (legacy app versions,
    // raw filesystem operations, restored backups).
    private static async Task AssignAdminToOrphanedFoldersAsync(
        ApplicationDbContext dbContext,
        CancellationToken ct)
    {
        var primaryAdmin = await dbContext.Users
            .Where(u => u.Role == "Admin" && u.IsActive)
            .OrderBy(u => u.CreatedAt)
            .FirstOrDefaultAsync(ct);

        if (primaryAdmin == null) return;

        var foldersWithPermissions = (await dbContext.FolderPermissions
            .Select(p => p.FolderId)
            .Distinct()
            .ToListAsync(ct))
            .ToHashSet();

        var orphaned = await dbContext.Folders
            .Where(f => !foldersWithPermissions.Contains(f.Id))
            .ToListAsync(ct);

        var toFix = orphaned
            .Where(f => !IsPersonalUserPath(f.Path) && !VirtualPath.IsStructuralContainer(f.Path))
            .ToList();

        if (toFix.Count == 0) return;

        foreach (var folder in toFix)
        {
            dbContext.FolderPermissions.Add(new FolderPermission
            {
                UserId = primaryAdmin.Id,
                FolderId = folder.Id,
                CanRead = true,
                CanWrite = true,
                CanDelete = true,
                CanManagePermissions = true,
                GrantedByUserId = primaryAdmin.Id
            });
        }

        await dbContext.SaveChangesAsync(ct);
        Console.WriteLine($"[INDEX] Auto-asignado admin '{primaryAdmin.Username}' como propietario de {toFix.Count} carpeta(s) huérfana(s).");
    }

    private static bool IsPersonalUserPath(string normalizedPath) =>
        normalizedPath.StartsWith("/assets/users/", StringComparison.OrdinalIgnoreCase);
}
