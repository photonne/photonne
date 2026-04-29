using System.Collections.Concurrent;
using System.Runtime.CompilerServices;
using System.Security.Claims;
using System.Text.Json;
using System.Threading.Channels;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Dtos;
using Scalar.AspNetCore;

namespace Photonne.Server.Api.Features.IndexAssets;

public class IndexAssetsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var serviceProvider = app.ServiceProvider;
        app.MapGet("/api/assets/index/stream", (
            [FromServices] DirectoryScanner directoryScanner,
            [FromServices] FileHashService hashService,
            [FromServices] ExifExtractorService exifService,
            [FromServices] ThumbnailGeneratorService thumbnailService,
            [FromServices] MediaRecognitionService mediaRecognitionService,
            [FromServices] IMlJobService mlJobService,
            [FromServices] SettingsService settingsService,
            [FromServices] ApplicationDbContext dbContext,
            [FromServices] BackgroundTaskManager backgroundTaskManager,
            HttpContext httpContext,
            CancellationToken cancellationToken) => HandleStream(directoryScanner, hashService, exifService, thumbnailService, mediaRecognitionService, mlJobService, settingsService, dbContext, serviceProvider, backgroundTaskManager,
                Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var uid) ? uid : Guid.Empty,
                cancellationToken))
        .WithName("IndexAssetsStream")
        .WithTags("Assets")
        .WithDescription("Streams the scanning process progress for the internal assets directory")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));

        app.MapGet("/api/assets/index", Handle)
        .CodeSample(
                codeSample: "curl -X GET \"http://localhost:5000/api/assets/index\" -H \"Accept: application/json\"",
                label: "cURL Example")
        .WithName("IndexAssets")
        .WithTags("Assets")
        .WithDescription("Scans the internal assets directory, extracts metadata, generates thumbnails, and updates the database with all found media files. Supports images (JPG, PNG, etc.) and videos (MP4, AVI, etc.)")
        .AddOpenApiOperationTransformer((operation, context, ct) =>
        {
            operation.Summary = "Scans the internal assets directory";
            operation.Description = "This endpoint recursively scans the internal assets directory (ASSETS_PATH), extracts metadata, generates thumbnails, and updates the database with all found media files. Supports images (JPG, PNG, etc.) and videos (MP4, AVI, etc.)";
            return Task.CompletedTask;
        })
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private async IAsyncEnumerable<IndexProgressUpdate> HandleStream(
        DirectoryScanner directoryScanner,
        FileHashService hashService,
        ExifExtractorService exifService,
        ThumbnailGeneratorService thumbnailService,
        MediaRecognitionService mediaRecognitionService,
        IMlJobService mlJobService,
        SettingsService settingsService,
        ApplicationDbContext dbContext,
        IServiceProvider serviceProvider,
        BackgroundTaskManager backgroundTaskManager,
        Guid userId,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var channel = Channel.CreateUnbounded<IndexProgressUpdate>();
        var indexStartTime = DateTime.UtcNow;
        var stats = new IndexStatistics();

        var entry = backgroundTaskManager.Register(BackgroundTaskType.IndexAssets);
        var taskCt = entry.Cts.Token;
        void Send(IndexProgressUpdate upd)
        {
            upd.TaskId = entry.Id;
            channel.Writer.TryWrite(upd);
            entry.Push(JsonSerializer.Serialize(upd), upd.Percentage, upd.Message);
        }

        // Background task to perform the scan
        _ = Task.Run(async () =>
        {
            try
            {
                using var scope = serviceProvider.CreateScope();
                var scopedSettingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();
                var scopedDbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                var scopedHashService = scope.ServiceProvider.GetRequiredService<FileHashService>();
                var scopedExifService = scope.ServiceProvider.GetRequiredService<ExifExtractorService>();
                var scopedMediaRecognitionService = scope.ServiceProvider.GetRequiredService<MediaRecognitionService>();
                var scopedThumbnailService = scope.ServiceProvider.GetRequiredService<ThumbnailGeneratorService>();
                var scopedMlJobService = scope.ServiceProvider.GetRequiredService<IMlJobService>();
                var notificationService = scope.ServiceProvider.GetRequiredService<INotificationService>();

                // Obtener la ruta interna del NAS (ASSETS_PATH)
                var directoryPath = scopedSettingsService.GetInternalAssetsPath();
                Console.WriteLine($"[SCAN] Indexando directorio interno: {directoryPath}");

                if (!Directory.Exists(directoryPath))
                {
                    Send(new IndexProgressUpdate
                    {
                        Message = $"Error: El directorio interno no existe: {directoryPath}",
                        IsCompleted = true
                    });
                    entry.Finish("Failed");
                    return;
                }

                Send(new IndexProgressUpdate { Message = "Iniciando descubrimiento de archivos...", Percentage = 0 });

                // Repair any folders that were created outside the app and have no owner
                await AssignAdminToOrphanedFoldersAsync(scopedDbContext, taskCt);

                // STEP 1: Recursive file discovery
                var scannedFilesList = (await directoryScanner.ScanDirectoryAsync(directoryPath, taskCt)).ToList();
                stats.TotalFilesFound = scannedFilesList.Count;

                Send(new IndexProgressUpdate { Message = $"Descubiertos {stats.TotalFilesFound} archivos. Procesando...", Percentage = 10, Statistics = MapToBlazorStats(stats) });

                // Load existing assets for differential comparison (without Exif to reduce memory usage)
                // Manejar duplicados: si hay múltiples assets con el mismo checksum, tomar el más reciente
                var allAssets = await dbContext.Assets
                    .Where(a => a.DeletedAt == null)
                    .ToListAsync(taskCt);
                var existingAssetsByChecksum = allAssets
                    .Where(a => !string.IsNullOrEmpty(a.Checksum))
                    .GroupBy(a => a.Checksum)
                    .ToDictionary(g => g.Key, g => g.OrderByDescending(a => a.ScannedAt).First());

                var existingAssetsByPath = allAssets
                    .Where(a => !string.IsNullOrEmpty(a.FullPath))
                    .GroupBy(a => a.FullPath)
                    .ToDictionary(g => g.Key, g => g.OrderByDescending(a => a.ScannedAt).First());

                // Lightweight set of asset IDs that already have DateTimeOriginal extracted
                var assetsWithDateTimeOriginal = await dbContext.AssetExifs
                    .Where(e => e.DateTimeOriginal != null)
                    .Select(e => e.AssetId)
                    .ToHashSetAsync(taskCt);

                // STEP 2 & 3: Process files
                var indexContext = new IndexContext
                {
                    AssetsToCreate = new List<Asset>(),
                    AssetsToUpdate = new List<Asset>(),
                    AssetsToDelete = new HashSet<string>(), // Ahora guardará las rutas virtualizadas de los archivos ENCONTRADOS
                    AllScannedAssets = new HashSet<Asset>(),
                    ProcessedDirectories = new HashSet<string>()
                };

                int processedCount = 0;

                foreach (var file in scannedFilesList)
                {
                    taskCt.ThrowIfCancellationRequested();

                    // Normalizar la ruta del archivo para comparar con la BD
                    var dbPath = await scopedSettingsService.VirtualizePathAsync(file.FullPath);
                    indexContext.AssetsToDelete.Add(dbPath); // Añadir a la lista de archivos que SI existen

                    var fileDirectory = Path.GetDirectoryName(file.FullPath);
                    if (!string.IsNullOrEmpty(fileDirectory))
                    {
                        var virtualFolder = await scopedSettingsService.VirtualizePathAsync(fileDirectory);
                        indexContext.ProcessedDirectories.Add(virtualFolder);
                    }

                    await EnsureFolderStructureForFileAsync(scopedDbContext, scopedSettingsService, file.FullPath, new HashSet<string>(), taskCt);

                    var changeResult = await VerifyFileChangesAsync(file, existingAssetsByPath, existingAssetsByChecksum, scopedHashService, scopedDbContext, stats, scopedSettingsService, taskCt);

                    if (!changeResult.ShouldSkip)
                    {
                        var asset = changeResult.Asset!;
                        var isNew = changeResult.IsNew;
                        if (ShouldExtractExif(asset, isNew, assetsWithDateTimeOriginal))
                            await ExtractExifMetadataAsync(asset, file.FullPath, exifService, stats, taskCt);

                        if (asset.Exif != null)
                            await DetectMediaTagsAsync(asset, file.FullPath, mediaRecognitionService, stats, taskCt);

                        if (!isNew)
                        {
                            indexContext.AssetsToUpdate.Add(asset);
                            indexContext.AllScannedAssets.Add(asset);
                        }
                        else
                        {
                            asset.ScannedAt = indexStartTime;
                            indexContext.AssetsToCreate.Add(asset);
                        }
                    }
                    else if (changeResult.ExistingAsset != null)
                    {
                        indexContext.AllScannedAssets.Add(changeResult.ExistingAsset);
                    }

                    processedCount++;
                    if (processedCount % 10 == 0 || processedCount == scannedFilesList.Count)
                    {
                        Send(new IndexProgressUpdate
                        {
                            Message = $"Procesando archivo {processedCount} de {stats.TotalFilesFound}...",
                            Percentage = 10 + (processedCount * 40.0 / scannedFilesList.Count),
                            Statistics = MapToBlazorStats(stats)
                        });
                    }
                }

                // STEP 4: Database operations and thumbnail generation
                Send(new IndexProgressUpdate { Message = "Guardando cambios y generando miniaturas...", Percentage = 50, Statistics = MapToBlazorStats(stats) });

                await ProcessDatabaseOperationsWithProgressAsync(indexContext, dbContext, thumbnailService, mediaRecognitionService, mlJobService, settingsService, Send, stats, taskCt);

                stats.IndexCompletedAt = DateTime.UtcNow;
                stats.IndexDuration = stats.IndexCompletedAt - indexStartTime;

                Send(new IndexProgressUpdate
                {
                    Message = "Indexación completada con éxito.",
                    Percentage = 100,
                    Statistics = MapToBlazorStats(stats),
                    IsCompleted = true
                });

                entry.Finish("Completed");

                if (userId != Guid.Empty)
                    await notificationService.CreateAsync(userId, NotificationType.JobCompleted,
                        "Indexación completada", "Indexación completada con éxito.");
            }
            catch (OperationCanceledException)
            {
                Send(new IndexProgressUpdate { Message = "Indexación cancelada.", IsCompleted = true, Percentage = stats.TotalFilesFound > 0 ? 50 : 0 });
                entry.Finish("Cancelled");
            }
            catch (Exception ex)
            {
                Send(new IndexProgressUpdate { Message = $"Error: {ex.Message}", IsCompleted = true });
                entry.Finish("Failed");
            }
            finally
            {
                channel.Writer.TryComplete();
            }
        }, taskCt);

        // Stream the updates from the channel
        await foreach (var update in channel.Reader.ReadAllAsync(cancellationToken))
        {
            yield return update;
        }
    }

    private IndexStatistics MapToBlazorStats(IndexStatistics stats) => stats;

    private async Task ProcessDatabaseOperationsWithProgressAsync(
        IndexContext context,
        ApplicationDbContext dbContext,
        ThumbnailGeneratorService thumbnailService,
        MediaRecognitionService mediaRecognitionService,
        IMlJobService mlJobService,
        SettingsService settingsService,
        Action<IndexProgressUpdate> send,
        IndexStatistics apiStats,
        CancellationToken cancellationToken)
    {
        var thumbnailWorkers = ParseWorkerSetting(
            await settingsService.GetSettingAsync("TaskSettings.ThumbnailWorkers", Guid.Empty, "2"));

        // STEP 4a: Cleanup - Remove orphaned assets
        await RemoveOrphanedAssetsAsync(context.AssetsToDelete, dbContext, apiStats, cancellationToken);
        send(new IndexProgressUpdate { Message = "Limpieza de archivos huérfanos completada.", Percentage = 60, Statistics = MapToBlazorStats(apiStats) });

        // STEP 4b & 4c: Insert new assets, then generate thumbnails in parallel with live progress
        if (context.AssetsToCreate.Any())
        {
            // Set folder IDs and persist assets first (sequential — DbContext not thread-safe)
            foreach (var asset in context.AssetsToCreate)
            {
                if (asset.FolderId == null && !string.IsNullOrEmpty(asset.FullPath))
                {
                    var dir = Path.GetDirectoryName(asset.FullPath);
                    var folder = await GetOrCreateFolderForPathAsync(dbContext, settingsService, dir, cancellationToken);
                    asset.FolderId = folder?.Id;
                }
            }
            dbContext.Assets.AddRange(context.AssetsToCreate);
            await dbContext.SaveChangesAsync(cancellationToken);

            // Resolve physical paths (sequential)
            var newWorkItems = new List<(Asset asset, string physicalPath)>(context.AssetsToCreate.Count);
            foreach (var asset in context.AssetsToCreate)
            {
                var p = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (!string.IsNullOrEmpty(p))
                    newWorkItems.Add((asset, p));
            }

            // Generate thumbnails in parallel — ConcurrentBag and Action<T> are thread-safe
            var newResults = new ConcurrentBag<(Guid assetId, List<AssetThumbnail> thumbnails)>();
            var newDone = 0;
            var newTotal = newWorkItems.Count;
            var newThumbnailCount = 0;
            await Parallel.ForEachAsync(
                newWorkItems,
                new ParallelOptions { MaxDegreeOfParallelism = thumbnailWorkers, CancellationToken = cancellationToken },
                async (item, ct) =>
                {
                    var thumbnails = await thumbnailService.GenerateThumbnailsAsync(item.physicalPath, item.asset.Id, ct);
                    newResults.Add((item.asset.Id, thumbnails));
                    Interlocked.Add(ref newThumbnailCount, thumbnails.Count);
                    apiStats.ThumbnailsGenerated = newThumbnailCount;
                    var done = Interlocked.Increment(ref newDone);
                    send(new IndexProgressUpdate
                    {
                        Message = $"Generando miniaturas nuevas ({done}/{newTotal})...",
                        Percentage = 60 + (done * 15.0 / newTotal),
                        Statistics = MapToBlazorStats(apiStats)
                    });
                });

            // Persist results and queue ML jobs (sequential)
            foreach (var (assetId, thumbnails) in newResults)
            {
                if (thumbnails.Any())
                    dbContext.AssetThumbnails.AddRange(thumbnails);
            }
            await dbContext.SaveChangesAsync(cancellationToken);
            await QueueMlJobsForNewAssetsAsync(context.AssetsToCreate, dbContext, mediaRecognitionService, mlJobService, apiStats, cancellationToken);
        }
        send(new IndexProgressUpdate { Message = "Nuevos archivos procesados.", Percentage = 75, Statistics = MapToBlazorStats(apiStats) });

        // STEP 4d & 4e: Regenerate missing thumbnails for updated assets in parallel with live progress
        if (context.AssetsToUpdate.Any())
        {
            await dbContext.SaveChangesAsync(cancellationToken);

            // Load DB thumbnails and build work items (sequential)
            var updateWorkItems = new List<(Asset asset, List<ThumbnailSize> missingSizes, string physicalPath)>();
            foreach (var asset in context.AssetsToUpdate)
            {
                await dbContext.Entry(asset).Collection(a => a.Thumbnails).LoadAsync(cancellationToken);
                var missing = thumbnailService.GetMissingThumbnailSizes(asset.Id);
                if (!missing.Any()) continue;
                var toRemove = asset.Thumbnails.Where(t => missing.Contains(t.Size)).ToList();
                if (toRemove.Any()) dbContext.AssetThumbnails.RemoveRange(toRemove);
                var p = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (!string.IsNullOrEmpty(p))
                    updateWorkItems.Add((asset, missing, p));
            }

            if (updateWorkItems.Any())
            {
                var updateResults = new ConcurrentBag<(Guid assetId, List<AssetThumbnail> thumbnails)>();
                var updateDone = 0;
                var updateTotal = updateWorkItems.Count;
                var regeneratedCount = 0;
                await Parallel.ForEachAsync(
                    updateWorkItems,
                    new ParallelOptions { MaxDegreeOfParallelism = thumbnailWorkers, CancellationToken = cancellationToken },
                    async (item, ct) =>
                    {
                        var all = await thumbnailService.GenerateThumbnailsAsync(item.physicalPath, item.asset.Id, ct);
                        var newThumbs = all.Where(t => item.missingSizes.Contains(t.Size)).ToList();
                        updateResults.Add((item.asset.Id, newThumbs));
                        Interlocked.Add(ref regeneratedCount, newThumbs.Count);
                        apiStats.ThumbnailsRegenerated = regeneratedCount;
                        var done = Interlocked.Increment(ref updateDone);
                        send(new IndexProgressUpdate
                        {
                            Message = $"Actualizando miniaturas ({done}/{updateTotal})...",
                            Percentage = 75 + (done * 10.0 / updateTotal),
                            Statistics = MapToBlazorStats(apiStats)
                        });
                    });

                foreach (var (assetId, thumbs) in updateResults)
                {
                    if (thumbs.Any())
                        dbContext.AssetThumbnails.AddRange(thumbs);
                }
                await dbContext.SaveChangesAsync(cancellationToken);
            }
        }
        send(new IndexProgressUpdate { Message = "Archivos actualizados.", Percentage = 85, Statistics = MapToBlazorStats(apiStats) });

        // STEP 4f: Verify thumbnails for unchanged assets in parallel with live progress
        var unchangedAssets = context.AllScannedAssets
            .Where(a => !context.AssetsToUpdate.Contains(a) && !context.AssetsToCreate.Contains(a))
            .ToList();

        if (unchangedAssets.Any())
        {
            var verifyWorkItems = new List<(Asset asset, List<ThumbnailSize> missingSizes, string physicalPath)>();
            foreach (var asset in unchangedAssets)
            {
                await dbContext.Entry(asset).Collection(a => a.Thumbnails).LoadAsync(cancellationToken);
                var missing = thumbnailService.GetMissingThumbnailSizes(asset.Id);
                if (!missing.Any()) continue;
                var toRemove = asset.Thumbnails.Where(t => missing.Contains(t.Size)).ToList();
                if (toRemove.Any()) dbContext.AssetThumbnails.RemoveRange(toRemove);
                var p = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (!string.IsNullOrEmpty(p))
                    verifyWorkItems.Add((asset, missing, p));
            }

            if (verifyWorkItems.Any())
            {
                var verifyResults = new ConcurrentBag<(Guid assetId, List<AssetThumbnail> thumbnails)>();
                var verifyDone = 0;
                var verifyTotal = verifyWorkItems.Count;
                await Parallel.ForEachAsync(
                    verifyWorkItems,
                    new ParallelOptions { MaxDegreeOfParallelism = thumbnailWorkers, CancellationToken = cancellationToken },
                    async (item, ct) =>
                    {
                        var all = await thumbnailService.GenerateThumbnailsAsync(item.physicalPath, item.asset.Id, ct);
                        var newThumbs = all.Where(t => item.missingSizes.Contains(t.Size)).ToList();
                        verifyResults.Add((item.asset.Id, newThumbs));
                        var done = Interlocked.Increment(ref verifyDone);
                        send(new IndexProgressUpdate
                        {
                            Message = $"Verificando miniaturas existentes ({done}/{verifyTotal})...",
                            Percentage = 85 + (done * 10.0 / verifyTotal),
                            Statistics = MapToBlazorStats(apiStats)
                        });
                    });

                foreach (var (assetId, thumbs) in verifyResults)
                {
                    if (thumbs.Any())
                    {
                        dbContext.AssetThumbnails.AddRange(thumbs);
                        apiStats.ThumbnailsRegenerated += thumbs.Count;
                    }
                }
                await dbContext.SaveChangesAsync(cancellationToken);
            }
        }

        await RemoveOrphanedFoldersAsync(context.ProcessedDirectories, dbContext, apiStats, cancellationToken);
        send(new IndexProgressUpdate { Message = "Limpieza de carpetas completada.", Percentage = 98, Statistics = MapToBlazorStats(apiStats) });
    }


    private async Task<IResult> Handle(
        [FromServices] DirectoryScanner directoryScanner,
        [FromServices] FileHashService hashService,
        [FromServices] ExifExtractorService exifService,
        [FromServices] ThumbnailGeneratorService thumbnailService,
        [FromServices] MediaRecognitionService mediaRecognitionService,
        [FromServices] IMlJobService mlJobService,
        [FromServices] SettingsService settingsService,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var indexStartTime = DateTime.UtcNow;
        var stats = new IndexStatistics();
        
        try
        {
            // Obtener la ruta interna del NAS (ASSETS_PATH)
            var directoryPath = settingsService.GetInternalAssetsPath();
            Console.WriteLine($"[SCAN] Indexando directorio interno: {directoryPath}");
            
            if (!Directory.Exists(directoryPath))
            {
                return Results.NotFound(new { error = $"El directorio interno no existe: {directoryPath}" });
            }

            // Repair any folders that were created outside the app and have no owner
            await AssignAdminToOrphanedFoldersAsync(dbContext, cancellationToken);

            // STEP 1: Recursive file discovery
            var scannedFiles = await DiscoverFilesAsync(directoryScanner, directoryPath, stats, cancellationToken);
            
            // Load existing assets for differential comparison (without Exif to reduce memory usage)
            // Manejar duplicados: si hay múltiples assets con el mismo checksum, tomar el más reciente
            var allAssets = await dbContext.Assets
                .Where(a => a.DeletedAt == null)
                .ToListAsync(cancellationToken);
            var existingAssetsByChecksum = allAssets
                .Where(a => !string.IsNullOrEmpty(a.Checksum))
                .GroupBy(a => a.Checksum)
                .ToDictionary(g => g.Key, g => g.OrderByDescending(a => a.ScannedAt).First());

            var existingAssetsByPath = allAssets
                .Where(a => !string.IsNullOrEmpty(a.FullPath))
                .GroupBy(a => a.FullPath)
                .ToDictionary(g => g.Key, g => g.OrderByDescending(a => a.ScannedAt).First());

            // Lightweight set of asset IDs that already have DateTimeOriginal extracted
            var assetsWithDateTimeOriginal = await dbContext.AssetExifs
                .Where(e => e.DateTimeOriginal != null)
                .Select(e => e.AssetId)
                .ToHashSetAsync(cancellationToken);

            // STEP 2 & 3: Process files (change detection, EXIF extraction, recognition)
            var indexContext = await ProcessFilesAsync(
                scannedFiles,
                existingAssetsByChecksum,
                existingAssetsByPath,
                dbContext,
                hashService,
                exifService,
                mediaRecognitionService,
                settingsService,
                indexStartTime,
                stats,
                assetsWithDateTimeOriginal,
                cancellationToken);
            
            // STEP 4: Database operations and thumbnail generation (atomic transaction)
            await ProcessDatabaseOperationsAsync(
                indexContext,
                dbContext,
                thumbnailService,
                mediaRecognitionService,
                mlJobService,
                settingsService,
                stats,
                cancellationToken);
            
            // STEP 6: Finalization
            stats.IndexCompletedAt = DateTime.UtcNow;
            stats.IndexDuration = stats.IndexCompletedAt - indexStartTime;
            
            var response = new IndexAssetsResponse
            {
                Statistics = MapToBlazorStats(stats),
                AssetsProcessed = indexContext.AssetsToCreate.Count + indexContext.AssetsToUpdate.Count,
                Message = $"Scan completed successfully. Processed {stats.NewFiles} new, {stats.UpdatedFiles} updated, {stats.OrphanedFilesRemoved} files and {stats.OrphanedFoldersRemoved} folders removed. Generated {stats.ThumbnailsGenerated} new thumbnails, regenerated {stats.ThumbnailsRegenerated} missing thumbnails."
            };
            
            return Results.Ok(response);
        }
        catch (DirectoryNotFoundException ex)
        {
            return Results.NotFound(new { error = ex.Message });
        }
        catch (ArgumentException ex)
        {
            return Results.BadRequest(new { error = ex.Message });
        }
        catch (Exception ex)
        {
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    // STEP 1: Recursive file discovery
    private async Task<IEnumerable<ScannedFile>> DiscoverFilesAsync(
        DirectoryScanner directoryScanner,
        string directoryPath,
        IndexStatistics stats,
        CancellationToken cancellationToken)
    {
        var scannedFiles = await directoryScanner.ScanDirectoryAsync(directoryPath, cancellationToken);
        stats.TotalFilesFound = scannedFiles.Count();
        return scannedFiles;
    }

    // STEP 2 & 3: Process files (change detection, EXIF extraction, recognition)
    private async Task<IndexContext> ProcessFilesAsync(
        IEnumerable<ScannedFile> scannedFiles,
        Dictionary<string, Asset> existingAssetsByChecksum,
        Dictionary<string, Asset> existingAssetsByPath,
        ApplicationDbContext dbContext,
        FileHashService hashService,
        ExifExtractorService exifService,
        MediaRecognitionService mediaRecognitionService,
        SettingsService settingsService,
        DateTime indexStartTime,
        IndexStatistics stats,
        HashSet<Guid> assetsWithDateTimeOriginal,
        CancellationToken cancellationToken)
    {
        var context = new IndexContext
        {
            AssetsToCreate = new List<Asset>(),
            AssetsToUpdate = new List<Asset>(),
            AssetsToDelete = new HashSet<string>(),
            AllScannedAssets = new HashSet<Asset>(),
            ProcessedDirectories = new HashSet<string>()
        };

        foreach (var file in scannedFiles)
        {
            cancellationToken.ThrowIfCancellationRequested();
            
            var deletePath = await settingsService.VirtualizePathAsync(file.FullPath);
            context.AssetsToDelete.Add(deletePath);
            
            // Ensure folder structure exists
            await EnsureFolderStructureForFileAsync(dbContext, settingsService, file.FullPath, context.ProcessedDirectories, cancellationToken);
            
            // STEP 2: Change verification (differential)
            var changeResult = await VerifyFileChangesAsync(
                file,
                existingAssetsByPath,
                existingAssetsByChecksum,
                hashService,
                dbContext,
                stats,
                settingsService,
                cancellationToken);
            
            if (changeResult.ShouldSkip)
            {
                if (changeResult.ExistingAsset != null)
                {
                    context.AllScannedAssets.Add(changeResult.ExistingAsset);
                }
                continue;
            }
            
            var asset = changeResult.Asset!;
            var isNew = changeResult.IsNew;
            
            // STEP 3: Extract EXIF metadata (only for new images)
            if (ShouldExtractExif(asset, isNew, assetsWithDateTimeOriginal))
            {
                await ExtractExifMetadataAsync(
                    asset,
                    file.FullPath,
                    exifService,
                    stats,
                    cancellationToken);
            }
            
            // STEP 3b: Basic recognition - Detect media type tags (only if EXIF exists)
            if (asset.Exif != null)
            {
                await DetectMediaTagsAsync(
                    asset,
                    file.FullPath,
                    mediaRecognitionService,
                    stats,
                    cancellationToken);
            }
            
            if (!isNew)
            {
                context.AssetsToUpdate.Add(asset);
                context.AllScannedAssets.Add(asset);
            }
            else
            {
                asset.ScannedAt = indexStartTime;
            }
        }
        
        return context;
    }

    private async Task EnsureFolderStructureForFileAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        string filePath,
        HashSet<string> processedDirectories,
        CancellationToken cancellationToken)
    {
        var fileDirectory = Path.GetDirectoryName(filePath);
        if (!string.IsNullOrEmpty(fileDirectory))
        {
            var virtualDirectory = NormalizeVirtualPath(await settingsService.VirtualizePathAsync(fileDirectory));
            if (!processedDirectories.Contains(virtualDirectory))
            {
                await EnsureFolderStructureExistsAsync(dbContext, settingsService, virtualDirectory, cancellationToken);
                processedDirectories.Add(virtualDirectory);
            }
        }
    }

    private async Task<FileChangeResult> VerifyFileChangesAsync(
        ScannedFile file,
        Dictionary<string, Asset> existingAssetsByPath,
        Dictionary<string, Asset> existingAssetsByChecksum,
        FileHashService hashService,
        ApplicationDbContext dbContext,
        IndexStatistics stats,
        SettingsService settingsService,
        CancellationToken cancellationToken)
    {
        // Normalizar la ruta del archivo indexado si está en la biblioteca gestionada para comparar con la BD
        var dbPath = await settingsService.VirtualizePathAsync(file.FullPath);

        var existingByPath = existingAssetsByPath.GetValueOrDefault(dbPath);
        Guid? ownerId = await GetValidatedOwnerIdAsync(dbPath, dbContext, cancellationToken);
        var fileDirectory = Path.GetDirectoryName(file.FullPath);
        var needsFullCheck = existingByPath == null || 
            hashService.HasFileChanged(file.FullPath, existingByPath.FileSize, existingByPath.FileModifiedAt);
        
        if (!needsFullCheck)
        {
            stats.SkippedUnchanged++;
            return new FileChangeResult { ShouldSkip = true, ExistingAsset = existingByPath };
        }
        
        // Calculate hash for change detection
        var checksum = await hashService.CalculateFileHashAsync(file.FullPath, cancellationToken);
        stats.HashesCalculated++;
        
        // Check if asset exists by checksum (handles moved/renamed files)
        var existingByChecksum = existingAssetsByChecksum.GetValueOrDefault(checksum);

        Asset? asset;
        bool isNew = false;

        // A checksum match at a different path is only a "move" if the old physical file no longer exists.
        // If the old file still exists, this is a genuine duplicate — treat the current file as new.
        bool isMove = false;
        if (existingByChecksum != null && existingByChecksum.FullPath != dbPath)
        {
            var oldPhysicalPath = await settingsService.ResolvePhysicalPathAsync(existingByChecksum.FullPath);
            isMove = !File.Exists(oldPhysicalPath);
        }

        if (isMove && existingByChecksum != null)
        {
            // File was moved/renamed - update path
            existingByChecksum.FullPath = dbPath;
            existingByChecksum.FileName = file.FileName;
            existingByChecksum.FileModifiedAt = file.FileModifiedAt;
            existingByChecksum.FileSize = file.FileSize;
            existingByChecksum.OwnerId = ownerId;
            var movedFolder = await GetOrCreateFolderForPathAsync(dbContext, settingsService, fileDirectory, cancellationToken);
            existingByChecksum.FolderId = movedFolder?.Id;
            asset = existingByChecksum;
            stats.MovedFiles++;
        }
        else if (existingByPath != null)
        {
            // File exists at same path - update if changed
            existingByPath.Checksum = checksum;
            existingByPath.FileSize = file.FileSize;
            existingByPath.FileModifiedAt = file.FileModifiedAt;
            existingByPath.OwnerId = ownerId;
            if (existingByPath.FolderId == null)
            {
                var existingFolder = await GetOrCreateFolderForPathAsync(dbContext, settingsService, fileDirectory, cancellationToken);
                existingByPath.FolderId = existingFolder?.Id;
            }
            asset = existingByPath;
            stats.UpdatedFiles++;
        }
        else
        {
            // New file
            var folder = await GetOrCreateFolderForPathAsync(dbContext, settingsService, fileDirectory, cancellationToken);
            
            asset = new Asset
            {
                FileName = file.FileName,
                FullPath = dbPath,
                FileSize = file.FileSize,
                Checksum = checksum,
                Type = file.AssetType,
                Extension = file.Extension,
                FileCreatedAt = file.FileCreatedAt,
                FileModifiedAt = file.FileModifiedAt,
                FolderId = folder?.Id,
                OwnerId = ownerId
            };
            
            isNew = true;
            stats.NewFiles++;
        }
        
        return new FileChangeResult { Asset = asset, IsNew = isNew };
    }

    // Método separado para decidir si debe extraerse EXIF
    private static bool ShouldExtractExif(Asset asset, bool isNew, HashSet<Guid> assetsWithDateTimeOriginal)
    {
        if (asset.Type != AssetType.Image && asset.Type != AssetType.Video)
            return false;

        if (isNew)
            return true;

        return !assetsWithDateTimeOriginal.Contains(asset.Id);
    }

    // Método que SOLO extrae EXIF (sin lógica de decisión)
    private async Task ExtractExifMetadataAsync(
        Asset asset,
        string filePath,
        ExifExtractorService exifService,
        IndexStatistics stats,
        CancellationToken cancellationToken)
    {
        var extractedExif = await exifService.ExtractExifAsync(filePath, cancellationToken);
        if (extractedExif != null)
        {
            asset.Exif = extractedExif;
            stats.ExifExtracted++;
        }
    }

    // Método que SOLO detecta tags (sin lógica de decisión)
    private async Task DetectMediaTagsAsync(
        Asset asset,
        string filePath,
        MediaRecognitionService mediaRecognitionService,
        IndexStatistics stats,
        CancellationToken cancellationToken)
    {
        if (asset.Exif == null)
            return;
        
        var detectedTags = await mediaRecognitionService.DetectMediaTypeAsync(
            filePath,
            asset.Exif,
            cancellationToken);
        
        if (detectedTags.Any())
        {
            asset.Tags = detectedTags.Select(t => new AssetTag
            {
                TagType = t,
                DetectedAt = DateTime.UtcNow
            }).ToList();
            stats.MediaTagsDetected += detectedTags.Count;
        }
    }

    // STEP 4: Database operations and thumbnail generation (atomic transaction)
    private async Task ProcessDatabaseOperationsAsync(
        IndexContext context,
        ApplicationDbContext dbContext,
        ThumbnailGeneratorService thumbnailService,
        MediaRecognitionService mediaRecognitionService,
        IMlJobService mlJobService,
        SettingsService settingsService,
        IndexStatistics stats,
        CancellationToken cancellationToken)
    {
        var thumbnailWorkers = ParseWorkerSetting(
            await settingsService.GetSettingAsync("TaskSettings.ThumbnailWorkers", Guid.Empty, "2"));

        using var transaction = await dbContext.Database.BeginTransactionAsync(cancellationToken);
        try
        {
            // STEP 4a: Insert new assets
            await InsertNewAssetsAsync(
                context.AssetsToCreate,
                dbContext,
                thumbnailService,
                mediaRecognitionService,
                mlJobService,
                settingsService,
                stats,
                thumbnailWorkers,
                cancellationToken);

            // STEP 4d: Update existing assets
            await UpdateExistingAssetsAsync(
                context.AssetsToUpdate,
                dbContext,
                thumbnailService,
                settingsService,
                stats,
                thumbnailWorkers,
                cancellationToken);

            // STEP 4f: Verify and regenerate thumbnails for ALL scanned assets
            await VerifyThumbnailsForAllAssetsAsync(
                context.AllScannedAssets,
                context.AssetsToUpdate,
                dbContext,
                thumbnailService,
                settingsService,
                stats,
                thumbnailWorkers,
                cancellationToken);
            
            // STEP 5: Cleanup - Remove duplicate assets (same checksum)
            // Se ejecuta después de salvar cambios previos para que la BD refleje el estado actual
            await dbContext.SaveChangesAsync(cancellationToken);
            await RemoveDuplicateAssetsAsync(
                dbContext,
                stats,
                settingsService,
                cancellationToken);
            
            // STEP 5b: Cleanup - Remove orphaned assets
            await RemoveOrphanedAssetsAsync(
                context.AssetsToDelete,
                dbContext,
                stats,
                cancellationToken);
            
            // STEP 5c: Cleanup - Remove orphaned folders
            await RemoveOrphanedFoldersAsync(
                context.ProcessedDirectories,
                dbContext,
                stats,
                cancellationToken);
            
            await dbContext.SaveChangesAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
        }
        catch
        {
            await transaction.RollbackAsync(cancellationToken);
            throw;
        }
    }

    private async Task InsertNewAssetsAsync(
        List<Asset> assetsToCreate,
        ApplicationDbContext dbContext,
        ThumbnailGeneratorService thumbnailService,
        MediaRecognitionService mediaRecognitionService,
        IMlJobService mlJobService,
        SettingsService settingsService,
        IndexStatistics stats,
        int thumbnailWorkers,
        CancellationToken cancellationToken)
    {
        if (!assetsToCreate.Any())
            return;

        // Set folder IDs for new assets (in case they weren't set during creation)
        foreach (var asset in assetsToCreate)
        {
            if (asset.FolderId == null && !string.IsNullOrEmpty(asset.FullPath))
            {
                var fileDirectory = Path.GetDirectoryName(asset.FullPath);
                var folder = await GetOrCreateFolderForPathAsync(dbContext, settingsService, fileDirectory, cancellationToken);
                asset.FolderId = folder?.Id;
            }
        }

        dbContext.Assets.AddRange(assetsToCreate);
        await dbContext.SaveChangesAsync(cancellationToken);

        // STEP 4b: Generate thumbnails for new assets
        await GenerateThumbnailsForNewAssetsAsync(
            assetsToCreate,
            dbContext,
            thumbnailService,
            settingsService,
            stats,
            thumbnailWorkers,
            cancellationToken);
        
        // STEP 4c: Queue ML jobs for new assets
        await QueueMlJobsForNewAssetsAsync(
            assetsToCreate,
            dbContext,
            mediaRecognitionService,
            mlJobService,
            stats,
            cancellationToken);
    }

    private async Task GenerateThumbnailsForNewAssetsAsync(
        List<Asset> assets,
        ApplicationDbContext dbContext,
        ThumbnailGeneratorService thumbnailService,
        SettingsService settingsService,
        IndexStatistics stats,
        int workerCount,
        CancellationToken cancellationToken)
    {
        if (!assets.Any())
            return;

        // Resolve physical paths first (sequential — string-only, but keeps things predictable)
        var workItems = new List<(Asset asset, string physicalPath)>(assets.Count);
        foreach (var asset in assets)
        {
            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (string.IsNullOrEmpty(physicalPath))
            {
                Console.WriteLine($"[WARNING] Could not resolve physical path for asset {asset.Id}: {asset.FullPath}");
                continue;
            }
            workItems.Add((asset, physicalPath));
        }

        // Generate thumbnails in parallel
        var results = new ConcurrentBag<(Guid assetId, List<AssetThumbnail> thumbnails)>();
        await Parallel.ForEachAsync(
            workItems,
            new ParallelOptions { MaxDegreeOfParallelism = workerCount, CancellationToken = cancellationToken },
            async (item, ct) =>
            {
                var thumbnails = await thumbnailService.GenerateThumbnailsAsync(item.physicalPath, item.asset.Id, ct);
                results.Add((item.asset.Id, thumbnails));
            });

        // Save results to DB (single-threaded — DbContext is not thread-safe)
        foreach (var (assetId, thumbnails) in results)
        {
            if (thumbnails.Any())
            {
                dbContext.AssetThumbnails.AddRange(thumbnails);
                stats.ThumbnailsGenerated += thumbnails.Count;
                Console.WriteLine($"[THUMBNAIL] Generated {thumbnails.Count} thumbnails for asset {assetId}");
            }
        }

        await dbContext.SaveChangesAsync(cancellationToken);
    }

    private async Task QueueMlJobsForNewAssetsAsync(
        List<Asset> assets,
        ApplicationDbContext dbContext,
        MediaRecognitionService mediaRecognitionService,
        IMlJobService mlJobService,
        IndexStatistics stats,
        CancellationToken cancellationToken)
    {
        var imageAssets = assets.Where(a => a.Type == AssetType.Image).ToList();
        if (!imageAssets.Any())
            return;
        
        foreach (var asset in imageAssets)
        {
            await dbContext.Entry(asset)
                .Reference(a => a.Exif)
                .LoadAsync(cancellationToken);
            
            if (mediaRecognitionService.ShouldTriggerMlJob(asset, asset.Exif))
            {
                await mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.FaceDetection, cancellationToken);
                await mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.ObjectRecognition, cancellationToken);
                await mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.SceneClassification, cancellationToken);
                await mlJobService.EnqueueMlJobAsync(asset.Id, MlJobType.TextRecognition, cancellationToken);
                stats.MlJobsQueued += 4;
            }
        }
    }

    private async Task UpdateExistingAssetsAsync(
        List<Asset> assetsToUpdate,
        ApplicationDbContext dbContext,
        ThumbnailGeneratorService thumbnailService,
        SettingsService settingsService,
        IndexStatistics stats,
        int thumbnailWorkers,
        CancellationToken cancellationToken)
    {
        if (!assetsToUpdate.Any())
            return;

        await dbContext.SaveChangesAsync(cancellationToken);

        // STEP 4e: Regenerate missing thumbnails for existing assets
        await RegenerateMissingThumbnailsAsync(
            assetsToUpdate,
            dbContext,
            thumbnailService,
            settingsService,
            stats,
            thumbnailWorkers,
            cancellationToken);
    }

    private async Task RegenerateMissingThumbnailsAsync(
        List<Asset> assets,
        ApplicationDbContext dbContext,
        ThumbnailGeneratorService thumbnailService,
        SettingsService settingsService,
        IndexStatistics stats,
        int workerCount,
        CancellationToken cancellationToken)
    {
        if (!assets.Any())
            return;

        // Phase 1: Load thumbnails from DB and prepare work items (sequential — DbContext not thread-safe)
        var workItems = new List<(Asset asset, List<ThumbnailSize> missingSizes, string physicalPath)>();
        foreach (var asset in assets)
        {
            await dbContext.Entry(asset).Collection(a => a.Thumbnails).LoadAsync(cancellationToken);
            var missingSizes = thumbnailService.GetMissingThumbnailSizes(asset.Id);
            if (!missingSizes.Any())
                continue;

            var thumbnailsToRemove = asset.Thumbnails.Where(t => missingSizes.Contains(t.Size)).ToList();
            if (thumbnailsToRemove.Any())
                dbContext.AssetThumbnails.RemoveRange(thumbnailsToRemove);

            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (!string.IsNullOrEmpty(physicalPath))
                workItems.Add((asset, missingSizes, physicalPath));
        }

        if (!workItems.Any())
            return;

        // Phase 2: Generate thumbnails in parallel
        var results = new ConcurrentBag<(Guid assetId, List<AssetThumbnail> newThumbnails)>();
        await Parallel.ForEachAsync(
            workItems,
            new ParallelOptions { MaxDegreeOfParallelism = workerCount, CancellationToken = cancellationToken },
            async (item, ct) =>
            {
                var allThumbnails = await thumbnailService.GenerateThumbnailsAsync(item.physicalPath, item.asset.Id, ct);
                var newThumbnails = allThumbnails.Where(t => item.missingSizes.Contains(t.Size)).ToList();
                results.Add((item.asset.Id, newThumbnails));
            });

        // Phase 3: Save to DB (single-threaded)
        foreach (var (assetId, newThumbnails) in results)
        {
            if (newThumbnails.Any())
            {
                dbContext.AssetThumbnails.AddRange(newThumbnails);
                stats.ThumbnailsRegenerated += newThumbnails.Count;
                Console.WriteLine($"[THUMBNAIL] Regenerated {newThumbnails.Count} thumbnails for asset {assetId}");
            }
        }

        await dbContext.SaveChangesAsync(cancellationToken);
    }

    private async Task VerifyThumbnailsForAllAssetsAsync(
        HashSet<Asset> allScannedAssets,
        List<Asset> assetsToUpdate,
        ApplicationDbContext dbContext,
        ThumbnailGeneratorService thumbnailService,
        SettingsService settingsService,
        IndexStatistics stats,
        int workerCount,
        CancellationToken cancellationToken)
    {
        var unchangedAssets = allScannedAssets
            .Where(a => !assetsToUpdate.Contains(a))
            .ToList();

        if (!unchangedAssets.Any())
            return;

        // Phase 1: Load thumbnails from DB and prepare work items (sequential)
        var workItems = new List<(Asset asset, List<ThumbnailSize> missingSizes, string physicalPath)>();
        foreach (var asset in unchangedAssets)
        {
            await dbContext.Entry(asset).Collection(a => a.Thumbnails).LoadAsync(cancellationToken);
            var missingSizes = thumbnailService.GetMissingThumbnailSizes(asset.Id);
            if (!missingSizes.Any())
                continue;

            var thumbnailsToRemove = asset.Thumbnails.Where(t => missingSizes.Contains(t.Size)).ToList();
            if (thumbnailsToRemove.Any())
                dbContext.AssetThumbnails.RemoveRange(thumbnailsToRemove);

            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (!string.IsNullOrEmpty(physicalPath))
                workItems.Add((asset, missingSizes, physicalPath));
        }

        if (!workItems.Any())
            return;

        // Phase 2: Generate thumbnails in parallel
        var results = new ConcurrentBag<(Guid assetId, List<AssetThumbnail> newThumbnails)>();
        await Parallel.ForEachAsync(
            workItems,
            new ParallelOptions { MaxDegreeOfParallelism = workerCount, CancellationToken = cancellationToken },
            async (item, ct) =>
            {
                var allThumbnails = await thumbnailService.GenerateThumbnailsAsync(item.physicalPath, item.asset.Id, ct);
                var newThumbnails = allThumbnails.Where(t => item.missingSizes.Contains(t.Size)).ToList();
                results.Add((item.asset.Id, newThumbnails));
            });

        // Phase 3: Save to DB (single-threaded)
        foreach (var (assetId, newThumbnails) in results)
        {
            if (newThumbnails.Any())
            {
                dbContext.AssetThumbnails.AddRange(newThumbnails);
                stats.ThumbnailsRegenerated += newThumbnails.Count;
                Console.WriteLine($"[THUMBNAIL] Regenerated {newThumbnails.Count} thumbnails for unchanged asset {assetId}");
            }
        }

        await dbContext.SaveChangesAsync(cancellationToken);
    }

    private async Task RemoveDuplicateAssetsAsync(
        ApplicationDbContext dbContext,
        IndexStatistics stats,
        SettingsService settingsService,
        CancellationToken cancellationToken)
    {
        // Encontrar assets duplicados por checksum
        // Usar AsNoTracking para evitar que EF mantenga demasiados objetos en memoria
        var allAssets = await dbContext.Assets
            .Where(a => !string.IsNullOrEmpty(a.Checksum))
            .AsNoTracking()
            .ToListAsync(cancellationToken);
        
        var duplicateGroups = allAssets
            .Select(a => new
            {
                Asset = a,
                OwnerId = TryGetOwnerIdFromVirtualPath(a.FullPath, out var parsedOwnerId) ? parsedOwnerId : (Guid?)null
            })
            .GroupBy(a => new { a.Asset.Checksum, a.OwnerId })
            .Where(g => g.Count() > 1)
            .ToList();
        
        if (!duplicateGroups.Any())
            return;
        
        var duplicatesToRemoveIds = new List<Guid>();
        var assetsToUpdate = new List<Asset>();
        
        foreach (var group in duplicateGroups)
        {
            // Mantener el más reciente (por ScannedAt) o el que tiene ID más bajo si no tienen ScannedAt
            var assetsInGroup = group
                .Select(g => g.Asset)
                .OrderByDescending(a => a.ScannedAt)
                .ThenBy(a => a.Id)
                .ToList();
            var assetToKeep = assetsInGroup.First();
            var duplicates = assetsInGroup.Skip(1).ToList();
            
            // Verificar que el asset a mantener tenga el archivo físico
            var physicalPath = await settingsService.ResolvePhysicalPathAsync(assetToKeep.FullPath);
            if (!File.Exists(physicalPath))
            {
                // Si el asset a mantener no tiene archivo, intentar encontrar uno en el grupo que sí lo tenga
                Asset? assetWithFile = null;
                foreach (var a in assetsInGroup.Skip(1))
                {
                    var path = await settingsService.ResolvePhysicalPathAsync(a.FullPath);
                    if (File.Exists(path))
                    {
                        assetWithFile = a;
                        break;
                    }
                }
                
                if (assetWithFile != null)
                {
                    // El que íbamos a mantener no existe, pero este sí. Intercambiamos.
                    duplicatesToRemoveIds.Add(assetToKeep.Id);
                    assetToKeep = assetWithFile;
                    duplicates = assetsInGroup.Where(a => a.Id != assetToKeep.Id).ToList();
                }
                else
                {
                    // Ningún asset del grupo tiene archivo físico... esto es raro.
                    // Mantendremos el original y dejaremos que RemoveOrphanedAssets lo limpie si es necesario.
                }
            }
            
            duplicatesToRemoveIds.AddRange(duplicates.Select(d => d.Id));
            
            // Si hay múltiples assets con el mismo checksum pero diferentes rutas, 
            // actualizar el asset a mantener con la ruta más reciente
            var mostRecentPath = assetsInGroup
                .OrderByDescending(a => a.ScannedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .First()
                .FullPath;
            
            if (assetToKeep.FullPath != mostRecentPath)
            {
                // Solo actualizar si realmente cambió
                var dbAssetToKeep = await dbContext.Assets.FindAsync(new object[] { assetToKeep.Id }, cancellationToken);
                if (dbAssetToKeep != null)
                {
                    dbAssetToKeep.FullPath = mostRecentPath;
                    dbAssetToKeep.FileName = Path.GetFileName(mostRecentPath);
                    dbAssetToKeep.OwnerId = await GetValidatedOwnerIdAsync(mostRecentPath, dbContext, cancellationToken);
                    assetsToUpdate.Add(dbAssetToKeep);
                }
            }
        }
        
        if (duplicatesToRemoveIds.Any())
        {
            // Eliminar duplicados de la base de datos de forma eficiente
            var duplicateThumbnails = await dbContext.AssetThumbnails
                .Where(t => duplicatesToRemoveIds.Contains(t.AssetId))
                .ToListAsync(cancellationToken);
            
            var duplicateExifs = await dbContext.AssetExifs
                .Where(e => duplicatesToRemoveIds.Contains(e.AssetId))
                .ToListAsync(cancellationToken);
            
            var assetsToRemove = await dbContext.Assets
                .Where(a => duplicatesToRemoveIds.Contains(a.Id) && a.DeletedAt == null)
                .ToListAsync(cancellationToken);
            
            dbContext.AssetThumbnails.RemoveRange(duplicateThumbnails);
            dbContext.AssetExifs.RemoveRange(duplicateExifs);
            dbContext.Assets.RemoveRange(assetsToRemove);
            
            await dbContext.SaveChangesAsync(cancellationToken);
            
            stats.DuplicateAssetsRemoved = duplicatesToRemoveIds.Count;
            Console.WriteLine($"[SCAN] Eliminados {duplicatesToRemoveIds.Count} assets duplicados");
        }
    }

    private async Task RemoveOrphanedAssetsAsync(
        HashSet<string> foundVirtualPaths,
        ApplicationDbContext dbContext,
        IndexStatistics stats,
        CancellationToken cancellationToken)
    {
        // Solo considerar assets internos (sin biblioteca externa).
        // Los assets de bibliotecas externas tienen sus propias rutas físicas
        // y su ciclo de vida se gestiona en ExternalLibraryScanService.
        var allAssetPaths = await dbContext.Assets
            .Where(a => a.DeletedAt == null && a.ExternalLibraryId == null)
            .Select(a => a.FullPath)
            .ToListAsync(cancellationToken);

        // Los huérfanos son los que están en la BD pero NO en el sistema de archivos (no encontrados en el scan)
        var orphanedPaths = allAssetPaths.Except(foundVirtualPaths).ToList();
        if (!orphanedPaths.Any())
            return;
        
        var orphanedAssets = await dbContext.Assets
            .Where(a => orphanedPaths.Contains(a.FullPath) && a.DeletedAt == null)
            .ToListAsync(cancellationToken);
        
        // Delete thumbnails and EXIF first (cascade should handle this, but explicit for safety)
        var orphanedAssetIds = orphanedAssets.Select(a => a.Id).ToList();
        var orphanedThumbnails = await dbContext.AssetThumbnails
            .Where(t => orphanedAssetIds.Contains(t.AssetId))
            .ToListAsync(cancellationToken);
        
        var orphanedExifs = await dbContext.AssetExifs
            .Where(e => orphanedAssetIds.Contains(e.AssetId))
            .ToListAsync(cancellationToken);
        
        dbContext.AssetThumbnails.RemoveRange(orphanedThumbnails);
        dbContext.AssetExifs.RemoveRange(orphanedExifs);
        dbContext.Assets.RemoveRange(orphanedAssets);
        
        stats.OrphanedFilesRemoved = orphanedAssets.Count;
    }

    private async Task RemoveOrphanedFoldersAsync(
        HashSet<string> foundVirtualDirectories,
        ApplicationDbContext dbContext,
        IndexStatistics stats,
        CancellationToken cancellationToken)
    {
        // Normalize found directories for comparison
        var normalizedFoundDirs = foundVirtualDirectories
            .Select(d => d.Replace('\\', '/').TrimEnd('/'))
            .Where(d => !string.IsNullOrEmpty(d))
            .ToHashSet();
        
        // Get all folder IDs that have assets
        var foldersWithAssets = await dbContext.Assets
            .Where(a => a.FolderId != null && a.DeletedAt == null)
            .Select(a => a.FolderId!.Value)
            .Distinct()
            .ToHashSetAsync(cancellationToken);
        
        // Solo considerar carpetas internas (sin biblioteca externa).
        // Las carpetas de bibliotecas externas se gestionan en ExternalLibraryScanService.
        var allFolders = await dbContext.Folders
            .Where(f => f.ExternalLibraryId == null)
            .Include(f => f.Assets)
            .Include(f => f.Permissions)
            .Include(f => f.SubFolders)
            .ToListAsync(cancellationToken);
        
        // Build a set of folder IDs that should be kept (have assets or are ancestors of folders with assets)
        var foldersToKeep = new HashSet<Guid>();
        foldersToKeep.UnionWith(foldersWithAssets);
        
        // Recursively add parent folders of folders with assets
        void AddParentFolders(Guid folderId)
        {
            var folder = allFolders.FirstOrDefault(f => f.Id == folderId);
            if (folder?.ParentFolderId != null && !foldersToKeep.Contains(folder.ParentFolderId.Value))
            {
                foldersToKeep.Add(folder.ParentFolderId.Value);
                AddParentFolders(folder.ParentFolderId.Value);
            }
        }
        
        foreach (var folderId in foldersWithAssets)
        {
            AddParentFolders(folderId);
        }
        
        var orphanedFolders = new List<Folder>();
        
        foreach (var folder in allFolders)
        {
            var normalizedPath = folder.Path.Replace('\\', '/').TrimEnd('/');
            
            bool hasAssets = folder.Assets.Any();
            bool hasPermissions = folder.Permissions.Any();
            bool existsInFilesystem = normalizedFoundDirs.Contains(normalizedPath);
            bool isAncestorOfFolderWithAssets = foldersToKeep.Contains(folder.Id);
            
            // Only delete if folder has no assets, no permissions, doesn't exist in filesystem, 
            // and is not an ancestor of a folder with assets
            if (!hasAssets && !hasPermissions && !existsInFilesystem && !isAncestorOfFolderWithAssets)
            {
                orphanedFolders.Add(folder);
            }
        }
        
        if (orphanedFolders.Any())
        {
            // Delete folders from bottom to top (children first) to avoid foreign key issues
            var foldersToDelete = orphanedFolders
                .OrderByDescending(f => f.Path.Count(c => c == '/' || c == '\\'))
                .ToList();
            
            dbContext.Folders.RemoveRange(foldersToDelete);
            stats.OrphanedFoldersRemoved = foldersToDelete.Count;
        }
    }

    private async Task<Folder?> GetOrCreateFolderForPathAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        string? folderPath,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(folderPath))
            return null;

        var virtualPath = await settingsService.VirtualizePathAsync(folderPath);
        var normalizedPath = NormalizeVirtualPath(virtualPath);
        if (string.IsNullOrEmpty(normalizedPath))
            return null;

        // Build the ancestor chain iteratively (deepest first, then reversed to root → leaf)
        var pathChain = new List<string>();
        var current = normalizedPath;
        while (!string.IsNullOrEmpty(current))
        {
            pathChain.Add(current);
            var parent = GetParentVirtualPath(current);
            if (string.IsNullOrEmpty(parent) || string.Equals(parent, current, StringComparison.OrdinalIgnoreCase))
                break;
            current = parent;
        }
        pathChain.Reverse(); // root → leaf

        // Pre-fetch all folders for these paths in one query
        var allPaths = pathChain.ToHashSet(StringComparer.OrdinalIgnoreCase);
        var existingFolders = await dbContext.Folders
            .Where(f => allPaths.Contains(f.Path))
            .ToDictionaryAsync(f => f.Path, f => f, StringComparer.OrdinalIgnoreCase, cancellationToken);

        // Physical-path fallback: a folder may have been stored with its original (non-virtualized)
        // path before virtualization was introduced. Check only for the leaf node.
        var physicalNormalized = NormalizeVirtualPath(folderPath);
        if (!string.Equals(physicalNormalized, normalizedPath, StringComparison.OrdinalIgnoreCase) &&
            !existingFolders.ContainsKey(normalizedPath))
        {
            var byPhysical = await dbContext.Folders
                .FirstOrDefaultAsync(f => f.Path == physicalNormalized, cancellationToken);
            if (byPhysical != null)
                existingFolders[physicalNormalized] = byPhysical;
        }

        Folder? parentFolder = null;
        Folder? resultFolder = null;

        foreach (var path in pathChain)
        {
            existingFolders.TryGetValue(path, out var folder);

            // For the leaf, also check the physical-path fallback bucket
            if (folder == null &&
                string.Equals(path, normalizedPath, StringComparison.OrdinalIgnoreCase) &&
                !string.Equals(physicalNormalized, normalizedPath, StringComparison.OrdinalIgnoreCase))
            {
                existingFolders.TryGetValue(physicalNormalized, out folder);
            }

            if (folder != null)
            {
                // Update path or parent if they drifted
                if (folder.ParentFolderId != parentFolder?.Id ||
                    !string.Equals(folder.Path, path, StringComparison.OrdinalIgnoreCase))
                {
                    folder.Path = path;
                    folder.Name = GetVirtualFolderName(path);
                    folder.ParentFolderId = parentFolder?.Id;
                    await dbContext.SaveChangesAsync(cancellationToken);
                }
            }
            else
            {
                folder = new Folder
                {
                    Path = path,
                    Name = GetVirtualFolderName(path),
                    ParentFolderId = parentFolder?.Id,
                    CreatedAt = DateTime.UtcNow
                };
                dbContext.Folders.Add(folder);
                await dbContext.SaveChangesAsync(cancellationToken);

                // Auto-assign primary admin as owner for folders outside personal user spaces.
                // Folders under /assets/users/{id}/ are owned implicitly by their user; everything
                // else (shared, external) must have an explicit owner so it's not invisible to all
                // non-admin users and won't be pruned as an orphan on subsequent scans.
                if (!IsPersonalUserPath(path))
                    await AssignAdminOwnerAsync(dbContext, folder.Id, cancellationToken);
            }

            parentFolder = folder;
            resultFolder = folder;
        }

        return resultFolder;
    }

    private static bool IsPersonalUserPath(string normalizedPath) =>
        normalizedPath.StartsWith("/assets/users/", StringComparison.OrdinalIgnoreCase);

    private static async Task AssignAdminOwnerAsync(
        ApplicationDbContext dbContext,
        Guid folderId,
        CancellationToken ct)
    {
        var primaryAdmin = await dbContext.Users
            .Where(u => u.Role == "Admin" && u.IsActive)
            .OrderBy(u => u.CreatedAt)
            .FirstOrDefaultAsync(ct);

        if (primaryAdmin == null) return;

        var exists = await dbContext.FolderPermissions
            .AnyAsync(p => p.UserId == primaryAdmin.Id && p.FolderId == folderId, ct);

        if (exists) return;

        dbContext.FolderPermissions.Add(new FolderPermission
        {
            UserId = primaryAdmin.Id,
            FolderId = folderId,
            CanRead = true,
            CanWrite = true,
            CanDelete = true,
            CanManagePermissions = true,
            GrantedByUserId = primaryAdmin.Id
        });

        await dbContext.SaveChangesAsync(ct);
    }

    /// <summary>
    /// Assigns the primary admin as owner to any existing non-personal folder that has no
    /// FolderPermission records at all. Runs once per indexing pass to repair orphaned folders
    /// created outside the app (e.g., via filesystem or a previous app version).
    /// </summary>
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
            .Where(f => !IsPersonalUserPath(f.Path))
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

    private async Task EnsureFolderStructureExistsAsync(
        ApplicationDbContext dbContext,
        SettingsService settingsService,
        string folderPath,
        CancellationToken cancellationToken)
    {
        await GetOrCreateFolderForPathAsync(dbContext, settingsService, folderPath, cancellationToken);
    }

    private static string NormalizeVirtualPath(string path)
    {
        return path.Replace('\\', '/').TrimEnd('/');
    }

    private static int ParseWorkerSetting(string value, int min = 1, int max = 32, int defaultValue = 2)
        => Math.Clamp(int.TryParse(value, out var n) ? n : defaultValue, min, max);

    /// <summary>
    /// Extracts OwnerId from a virtual path and validates the user exists in the DB.
    /// Falls back to the primary admin if the user does not exist or path is not a user path.
    /// </summary>
    private static async Task<Guid?> GetValidatedOwnerIdAsync(
        string virtualPath,
        ApplicationDbContext dbContext,
        CancellationToken ct)
    {
        if (TryGetOwnerIdFromVirtualPath(virtualPath, out var parsedOwnerId))
        {
            var exists = await dbContext.Users.AnyAsync(u => u.Id == parsedOwnerId, ct);
            if (exists)
                return parsedOwnerId;
        }

        // Fallback: primary admin (prevents FK violations for shared/external directories)
        var primaryAdminId = await dbContext.Users
            .Where(u => u.IsPrimaryAdmin)
            .Select(u => (Guid?)u.Id)
            .FirstOrDefaultAsync(ct);

        if (primaryAdminId != null)
            return primaryAdminId;

        return await dbContext.Users
            .Where(u => u.Role == "Admin" && u.IsActive)
            .OrderBy(u => u.CreatedAt)
            .Select(u => (Guid?)u.Id)
            .FirstOrDefaultAsync(ct);
    }

    private static bool TryGetOwnerIdFromVirtualPath(string path, out Guid ownerId)
    {
        ownerId = Guid.Empty;
        if (string.IsNullOrWhiteSpace(path))
        {
            return false;
        }

        var normalized = path.Replace('\\', '/').TrimStart('/');
        if (!normalized.StartsWith("assets/users/", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        var parts = normalized.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length < 3)
        {
            return false;
        }

        return Guid.TryParse(parts[2], out ownerId);
    }

    private static string GetParentVirtualPath(string path)
    {
        var normalized = NormalizeVirtualPath(path);
        var lastSlash = normalized.LastIndexOf('/');
        if (lastSlash <= 0)
        {
            return string.Empty;
        }
        return normalized.Substring(0, lastSlash);
    }

    private static string GetVirtualFolderName(string path)
    {
        var normalized = NormalizeVirtualPath(path);
        var lastSlash = normalized.LastIndexOf('/');
        return lastSlash >= 0 ? normalized[(lastSlash + 1)..] : normalized;
    }
}

// Helper classes for refactoring
internal class IndexContext
{
    public List<Asset> AssetsToCreate { get; set; } = new();
    public List<Asset> AssetsToUpdate { get; set; } = new();
    public HashSet<string> AssetsToDelete { get; set; } = new();
    public HashSet<Asset> AllScannedAssets { get; set; } = new();
    public HashSet<string> ProcessedDirectories { get; set; } = new();
}

internal class FileChangeResult
{
    public Asset? Asset { get; set; }
    public bool IsNew { get; set; }
    public bool ShouldSkip { get; set; }
    public Asset? ExistingAsset { get; set; }
}

