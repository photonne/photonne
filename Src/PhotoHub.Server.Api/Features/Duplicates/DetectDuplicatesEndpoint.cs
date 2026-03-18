using System.Runtime.CompilerServices;
using System.Threading.Channels;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Models;
using PhotoHub.Server.Api.Shared.Services;
using PhotoHub.Client.Shared.Models;

namespace PhotoHub.Server.Api.Features.Duplicates;

public class DetectDuplicatesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var serviceProvider = app.ServiceProvider;
        app.MapGet("/api/assets/duplicates/stream", (
            [FromQuery] bool cleanup,
            [FromQuery] bool physical,
            CancellationToken cancellationToken) =>
            physical
                ? HandlePhysicalStream(serviceProvider, cancellationToken)
                : HandleDbStream(cleanup, serviceProvider, cancellationToken))
        .WithName("DetectDuplicatesStream")
        .WithTags("Assets")
        .WithDescription("Streams duplicate detection. physical=true scans disk files; cleanup=true removes duplicate DB records.")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    // -------------------------------------------------------------------------
    // DB mode: compare stored checksums, optionally clean up duplicate records
    // -------------------------------------------------------------------------
    private async IAsyncEnumerable<DuplicatesProgressUpdate> HandleDbStream(
        bool cleanup,
        IServiceProvider serviceProvider,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var channel = Channel.CreateUnbounded<DuplicatesProgressUpdate>();

        _ = Task.Run(async () =>
        {
            try
            {
                using var scope = serviceProvider.CreateScope();
                var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();

                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                {
                    Message = "Cargando assets desde la base de datos...",
                    Percentage = 0
                }, cancellationToken);

                var assets = await dbContext.Assets
                    .Where(a => a.Checksum != null && a.Checksum != string.Empty)
                    .OrderBy(a => a.CreatedDate)
                    .Select(a => new { a.Id, a.FullPath, a.FileName, a.Checksum, a.FileSize, a.ScannedAt })
                    .ToListAsync(cancellationToken);

                var stats = new DuplicatesJobStatistics { TotalAssets = assets.Count };

                var duplicateGroups = assets
                    .GroupBy(a => a.Checksum!)
                    .Where(g => g.Count() > 1)
                    .ToList();

                stats.DuplicateGroups = duplicateGroups.Count;
                stats.DuplicateAssets = duplicateGroups.Sum(g => g.Count() - 1);

                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                {
                    Message = duplicateGroups.Count == 0
                        ? "No se encontraron duplicados."
                        : $"Encontrados {duplicateGroups.Count} grupos de duplicados ({stats.DuplicateAssets} copias redundantes).",
                    Percentage = cleanup ? 10 : 100,
                    Statistics = Clone(stats),
                    IsCompleted = !cleanup || duplicateGroups.Count == 0
                }, cancellationToken);

                if (!cleanup || duplicateGroups.Count == 0)
                {
                    channel.Writer.Complete();
                    return;
                }

                int groupsProcessed = 0;
                foreach (var group in duplicateGroups)
                {
                    if (cancellationToken.IsCancellationRequested) break;

                    try
                    {
                        var groupList = group.ToList();

                        var canonical = groupList
                            .OrderByDescending(a => a.ScannedAt)
                            .ThenBy(a => a.Id)
                            .First();

                        var canonicalPhysicalPath = await settingsService.ResolvePhysicalPathAsync(canonical.FullPath);
                        if (!File.Exists(canonicalPhysicalPath))
                        {
                            foreach (var candidate in groupList.Where(a => a.Id != canonical.Id))
                            {
                                var candidatePath = await settingsService.ResolvePhysicalPathAsync(candidate.FullPath);
                                if (File.Exists(candidatePath))
                                {
                                    canonical = candidate;
                                    break;
                                }
                            }
                        }

                        var toRemove = groupList.Where(a => a.Id != canonical.Id).ToList();
                        foreach (var dup in toRemove)
                        {
                            var dupAsset = await dbContext.Assets.FindAsync([dup.Id], cancellationToken);
                            if (dupAsset == null) continue;

                            var thumbnails = await dbContext.AssetThumbnails
                                .Where(t => t.AssetId == dup.Id)
                                .ToListAsync(cancellationToken);
                            dbContext.AssetThumbnails.RemoveRange(thumbnails);

                            var exif = await dbContext.AssetExifs
                                .FirstOrDefaultAsync(e => e.AssetId == dup.Id, cancellationToken);
                            if (exif != null)
                                dbContext.AssetExifs.Remove(exif);

                            dbContext.Assets.Remove(dupAsset);
                            stats.Removed++;
                            stats.BytesReclaimed += dup.FileSize;
                        }

                        await dbContext.SaveChangesAsync(cancellationToken);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[DUPLICATES] Error procesando grupo {group.Key}: {ex.Message}");
                    }

                    groupsProcessed++;
                    var pct = 10 + (double)groupsProcessed / duplicateGroups.Count * 90;

                    await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                    {
                        Message = $"Procesado grupo {groupsProcessed}/{duplicateGroups.Count}",
                        Percentage = pct,
                        Statistics = Clone(stats)
                    }, cancellationToken);
                }

                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                {
                    Message = BuildDbCompletionMessage(stats),
                    Percentage = 100,
                    Statistics = Clone(stats),
                    IsCompleted = true
                }, cancellationToken);
            }
            catch (OperationCanceledException)
            {
                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate { Message = "Proceso cancelado.", IsCompleted = true });
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DUPLICATES] Error fatal: {ex.Message}");
                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate { Message = $"Error: {ex.Message}", IsCompleted = true });
            }
            finally { channel.Writer.Complete(); }
        }, cancellationToken);

        await foreach (var update in channel.Reader.ReadAllAsync(cancellationToken))
            yield return update;
    }

    // -------------------------------------------------------------------------
    // Physical mode: scan disk, hash files, compare — analysis only
    // -------------------------------------------------------------------------
    private async IAsyncEnumerable<DuplicatesProgressUpdate> HandlePhysicalStream(
        IServiceProvider serviceProvider,
        [EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var channel = Channel.CreateUnbounded<DuplicatesProgressUpdate>();

        _ = Task.Run(async () =>
        {
            try
            {
                using var scope = serviceProvider.CreateScope();
                var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
                var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();
                var hashService = scope.ServiceProvider.GetRequiredService<FileHashService>();
                var scanner = scope.ServiceProvider.GetRequiredService<DirectoryScanner>();

                var assetsPath = settingsService.GetInternalAssetsPath();

                if (!Directory.Exists(assetsPath))
                {
                    await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                    {
                        Message = $"Error: el directorio de assets no existe: {assetsPath}",
                        IsCompleted = true
                    }, cancellationToken);
                    channel.Writer.Complete();
                    return;
                }

                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                {
                    Message = "Escaneando directorio de assets...",
                    Percentage = 0
                }, cancellationToken);

                var scannedFiles = (await scanner.ScanDirectoryAsync(assetsPath, cancellationToken)).ToList();

                var stats = new DuplicatesJobStatistics { TotalAssets = scannedFiles.Count };

                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                {
                    Message = $"Encontrados {scannedFiles.Count} archivos. Cargando índice de la base de datos...",
                    Percentage = 2,
                    Statistics = Clone(stats)
                }, cancellationToken);

                // Build a DB lookup: virtualized path → (Id, Checksum, FileSize, ModifiedDate)
                var dbIndex = await dbContext.Assets
                    .Where(a => a.Checksum != null && a.Checksum != string.Empty)
                    .Select(a => new { a.Id, a.FullPath, a.Checksum, a.FileSize, a.ModifiedDate })
                    .ToListAsync(cancellationToken);

                var dbByPath = dbIndex.ToDictionary(
                    a => a.FullPath,
                    a => a,
                    StringComparer.OrdinalIgnoreCase);

                // Hash each file: use stored checksum when unchanged, recalculate otherwise
                var fileHashes = new List<(string PhysicalPath, string VirtualPath, string Hash, long Size, bool IsIndexed, Guid? AssetId)>();
                int processed = 0;

                foreach (var file in scannedFiles)
                {
                    if (cancellationToken.IsCancellationRequested) break;

                    try
                    {
                        var virtualPath = await settingsService.VirtualizePathAsync(file.FullPath);
                        string hash;
                        bool isIndexed = false;
                        Guid? assetId = null;

                        if (dbByPath.TryGetValue(virtualPath, out var dbEntry))
                        {
                            isIndexed = true;
                            assetId = dbEntry.Id;
                            // Use stored checksum if file hasn't changed
                            if (!hashService.HasFileChanged(file.FullPath, dbEntry.FileSize, dbEntry.ModifiedDate))
                                hash = dbEntry.Checksum!;
                            else
                                hash = await hashService.CalculateFileHashAsync(file.FullPath, cancellationToken);
                        }
                        else
                        {
                            stats.UnindexedFiles++;
                            hash = await hashService.CalculateFileHashAsync(file.FullPath, cancellationToken);
                        }

                        fileHashes.Add((file.FullPath, virtualPath, hash, file.FileSize, isIndexed, assetId));
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[DUPLICATES-PHYSICAL] Error en {file.FullPath}: {ex.Message}");
                    }

                    processed++;

                    // Emit progress every 25 files to keep UI responsive without flooding
                    if (processed % 25 == 0 || processed == scannedFiles.Count)
                    {
                        var pct = 2 + (double)processed / scannedFiles.Count * 88;
                        await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                        {
                            Message = $"Analizando archivos: {processed}/{scannedFiles.Count}",
                            Percentage = pct,
                            Statistics = Clone(stats)
                        }, cancellationToken);
                    }
                }

                // Group by hash, emit each duplicate group to the client
                var duplicateGroups = fileHashes
                    .GroupBy(f => f.Hash)
                    .Where(g => g.Count() > 1)
                    .ToList();

                stats.DuplicateGroups = duplicateGroups.Count;
                stats.DuplicateAssets = duplicateGroups.Sum(g => g.Count() - 1);
                stats.BytesReclaimed = duplicateGroups.Sum(g =>
                    g.OrderByDescending(f => f.Size).Skip(1).Sum(f => f.Size));

                // Stream each group so the client can build the comparison UI
                foreach (var group in duplicateGroups)
                {
                    if (cancellationToken.IsCancellationRequested) break;

                    var groupModel = new PhysicalDuplicateGroup
                    {
                        Hash = group.Key,
                        Files = group.Select(f => new PhysicalDuplicateFile
                        {
                            PhysicalPath = f.PhysicalPath,
                            VirtualPath = f.VirtualPath,
                            FileName = System.IO.Path.GetFileName(f.PhysicalPath),
                            Directory = System.IO.Path.GetDirectoryName(f.PhysicalPath) ?? string.Empty,
                            FileSize = f.Size,
                            ModifiedDate = File.GetLastWriteTimeUtc(f.PhysicalPath),
                            IsIndexed = f.IsIndexed,
                            AssetId = f.AssetId
                        }).ToList()
                    };

                    await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                    {
                        Message = string.Empty,
                        Percentage = 90,
                        Statistics = Clone(stats),
                        FoundGroup = groupModel
                    }, cancellationToken);
                }

                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate
                {
                    Message = BuildPhysicalCompletionMessage(stats),
                    Percentage = 100,
                    Statistics = Clone(stats),
                    IsCompleted = true
                }, cancellationToken);
            }
            catch (OperationCanceledException)
            {
                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate { Message = "Proceso cancelado.", IsCompleted = true });
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[DUPLICATES-PHYSICAL] Error fatal: {ex.Message}");
                await channel.Writer.WriteAsync(new DuplicatesProgressUpdate { Message = $"Error: {ex.Message}", IsCompleted = true });
            }
            finally { channel.Writer.Complete(); }
        }, cancellationToken);

        await foreach (var update in channel.Reader.ReadAllAsync(cancellationToken))
            yield return update;
    }

    private static string BuildDbCompletionMessage(DuplicatesJobStatistics stats)
    {
        if (stats.Removed == 0)
            return "Completado — no se eliminó ningún duplicado.";
        return $"Completado — {stats.Removed} registros eliminados ({FormatBytes(stats.BytesReclaimed)} recuperados).";
    }

    private static string BuildPhysicalCompletionMessage(DuplicatesJobStatistics stats)
    {
        if (stats.DuplicateGroups == 0)
            return stats.UnindexedFiles > 0
                ? $"No se encontraron duplicados. {stats.UnindexedFiles} archivos sin indexar."
                : "No se encontraron duplicados en disco.";

        var parts = new List<string>
        {
            $"{stats.DuplicateGroups} grupos de duplicados",
            $"{stats.DuplicateAssets} copias redundantes ({FormatBytes(stats.BytesReclaimed)} en disco)"
        };
        if (stats.UnindexedFiles > 0)
            parts.Add($"{stats.UnindexedFiles} archivos sin indexar");

        return $"Completado — {string.Join(", ", parts)}.";
    }

    private static string FormatBytes(long bytes)
    {
        if (bytes >= 1_073_741_824) return $"{bytes / 1_073_741_824.0:F1} GB";
        if (bytes >= 1_048_576) return $"{bytes / 1_048_576.0:F1} MB";
        if (bytes >= 1_024) return $"{bytes / 1_024.0:F1} KB";
        return $"{bytes} B";
    }

    private static DuplicatesJobStatistics Clone(DuplicatesJobStatistics s) => new()
    {
        TotalAssets = s.TotalAssets,
        DuplicateGroups = s.DuplicateGroups,
        DuplicateAssets = s.DuplicateAssets,
        Removed = s.Removed,
        BytesReclaimed = s.BytesReclaimed,
        UnindexedFiles = s.UnindexedFiles
    };
}
