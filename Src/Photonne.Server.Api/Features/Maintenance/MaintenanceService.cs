using System.Text;
using Microsoft.EntityFrameworkCore;
using Photonne.Client.Web.Models;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Maintenance;

/// <summary>
/// Core logic for the admin maintenance tasks. Each method scans the whole
/// asset/folder set and can run for minutes on a large library, so the work
/// lives here (behind an optional progress callback) rather than inline in the
/// HTTP handler. Two callers share it:
///   • the legacy synchronous POST /api/admin/maintenance/{kind} (back-compat),
///     which passes a null callback and just awaits the final result;
///   • the streaming GET /api/admin/maintenance/{kind}/stream, which runs it on
///     a background Task and forwards each progress update over NDJSON.
/// The logic is identical to the former MaintenanceEndpoint handlers; only the
/// per-iteration progress emission is new.
/// </summary>
public class MaintenanceService
{
    private const string ThumbnailsBasePath = "/data/thumbnails";

    private readonly ApplicationDbContext _dbContext;
    private readonly SettingsService _settingsService;

    public MaintenanceService(ApplicationDbContext dbContext, SettingsService settingsService)
    {
        _dbContext = dbContext;
        _settingsService = settingsService;
    }

    /// <summary>Runs the task identified by <paramref name="kind"/> (the URL slug).
    /// Returns null for an unknown kind.</summary>
    public Task<MaintenanceTaskResult>? Run(
        string kind,
        bool dryRun,
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct) => kind switch
    {
        "orphan-thumbnails" => CleanOrphanThumbnailsAsync(onProgress, ct),
        "missing-files" => MarkMissingFilesAsync(onProgress, ct),
        "recalculate-sizes" => RecalculateSizesAsync(onProgress, ct),
        "empty-trash" => EmptyGlobalTrashAsync(onProgress, ct),
        "purge-missing" => PurgeMissingAsync(dryRun, onProgress, ct),
        _ => null
    };

    private static void Report(Action<MaintenanceProgressUpdate>? onProgress,
        string message, double percentage, int processed, int affected)
    {
        onProgress?.Invoke(new MaintenanceProgressUpdate
        {
            Message = message,
            Percentage = percentage,
            Processed = processed,
            Affected = affected
        });
    }

    // ─── Orphan thumbnails ────────────────────────────────────────────────────

    public async Task<MaintenanceTaskResult> CleanOrphanThumbnailsAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        if (!Directory.Exists(ThumbnailsBasePath))
            return new MaintenanceTaskResult
            {
                Success = true,
                Message = "El directorio de miniaturas no existe. Nada que limpiar.",
                Processed = 0,
                Affected = 0
            };

        Report(onProgress, "Escaneando miniaturas…", 0, 0, 0);

        // Collect all asset IDs that have thumbnail records in the database
        var knownAssetIds = await _dbContext.AssetThumbnails
            .AsNoTracking()
            .Select(t => t.AssetId)
            .Distinct()
            .ToHashSetAsync(ct);

        // Also collect all asset IDs that exist in the Assets table
        var existingAssetIds = await _dbContext.Assets
            .AsNoTracking()
            .Select(a => a.Id)
            .ToHashSetAsync(ct);

        var dirs = Directory.GetDirectories(ThumbnailsBasePath);
        int total = dirs.Length;
        int processed = 0;
        int deleted = 0;
        var errors = 0;

        // Each subdirectory in the thumbnails folder is named after an asset GUID
        foreach (var dir in dirs)
        {
            ct.ThrowIfCancellationRequested();
            processed++;

            var dirName = Path.GetFileName(dir);
            if (Guid.TryParse(dirName, out var assetId)
                // Orphan: neither in Assets table nor referenced by any AssetThumbnail record
                && !existingAssetIds.Contains(assetId) && !knownAssetIds.Contains(assetId))
            {
                try
                {
                    Directory.Delete(dir, recursive: true);
                    deleted++;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[MAINTENANCE] Error deleting orphan thumbnail dir {dir}: {ex.Message}");
                    errors++;
                }
            }

            if (processed % 50 == 0 || processed == total)
                Report(onProgress, $"Revisados {processed}/{total} directorios — {deleted} eliminados",
                    total > 0 ? (double)processed / total * 100 : 100, processed, deleted);
        }

        var message = deleted == 0
            ? $"Revisados {processed} directorios. No se encontraron miniaturas huérfanas."
            : $"Revisados {processed} directorios. {deleted} eliminados.{(errors > 0 ? $" {errors} errores." : "")}";

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = deleted
        };
    }

    // ─── Mark missing files ───────────────────────────────────────────────────

    public async Task<MaintenanceTaskResult> MarkMissingFilesAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        Report(onProgress, "Escaneando biblioteca…", 0, 0, 0);

        var assets = await _dbContext.Assets
            .Where(a => a.DeletedAt == null && !a.IsFileMissing)
            .Select(a => new { a.Id, a.FullPath })
            .ToListAsync(ct);

        int total = assets.Count;
        int processed = 0;
        int markedMissing = 0;

        foreach (var asset in assets)
        {
            ct.ThrowIfCancellationRequested();
            processed++;

            var physicalPath = await _settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (!File.Exists(physicalPath))
            {
                await _dbContext.Assets
                    .Where(a => a.Id == asset.Id)
                    .ExecuteUpdateAsync(s => s.SetProperty(a => a.IsFileMissing, true), ct);
                markedMissing++;
            }

            if (processed % 100 == 0 || processed == total)
                Report(onProgress, $"Revisados {processed}/{total} activos — {markedMissing} ausentes",
                    total > 0 ? (double)processed / total * 100 : 100, processed, markedMissing);
        }

        var message = markedMissing == 0
            ? $"Revisados {processed} activos. Todos los archivos están presentes."
            : $"Revisados {processed} activos. {markedMissing} marcados como faltantes.";

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = markedMissing
        };
    }

    // ─── Recalculate sizes ────────────────────────────────────────────────────

    public async Task<MaintenanceTaskResult> RecalculateSizesAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        Report(onProgress, "Escaneando biblioteca…", 0, 0, 0);

        var assets = await _dbContext.Assets
            .Where(a => a.DeletedAt == null && !a.IsFileMissing)
            .Select(a => new { a.Id, a.FullPath, a.FileSize })
            .ToListAsync(ct);

        int total = assets.Count;
        int processed = 0;
        int updated = 0;

        foreach (var asset in assets)
        {
            ct.ThrowIfCancellationRequested();
            processed++;

            var physicalPath = await _settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (File.Exists(physicalPath))
            {
                var actualSize = new FileInfo(physicalPath).Length;
                if (actualSize != asset.FileSize)
                {
                    await _dbContext.Assets
                        .Where(a => a.Id == asset.Id)
                        .ExecuteUpdateAsync(s => s.SetProperty(a => a.FileSize, actualSize), ct);
                    updated++;
                }
            }

            if (processed % 100 == 0 || processed == total)
                Report(onProgress, $"Revisados {processed}/{total} activos — {updated} actualizados",
                    total > 0 ? (double)processed / total * 100 : 100, processed, updated);
        }

        var message = updated == 0
            ? $"Revisados {processed} activos. Todos los tamaños son correctos."
            : $"Revisados {processed} activos. {updated} actualizados.";

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = updated
        };
    }

    // ─── Empty global trash ───────────────────────────────────────────────────

    public async Task<MaintenanceTaskResult> EmptyGlobalTrashAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        Report(onProgress, "Escaneando papelera…", 0, 0, 0);

        var assets = await _dbContext.Assets
            .Include(a => a.Thumbnails)
            .Where(a => a.DeletedAt != null)
            .ToListAsync(ct);

        if (assets.Count == 0)
        {
            return new MaintenanceTaskResult
            {
                Success = true,
                Message = "La papelera ya está vacía.",
                Processed = 0,
                Affected = 0
            };
        }

        int total = assets.Count;
        int deleted = 0;

        foreach (var asset in assets)
        {
            ct.ThrowIfCancellationRequested();

            // No borrar archivos físicos de bibliotecas externas — no son propiedad de Photonne
            if (!asset.ExternalLibraryId.HasValue)
            {
                var physicalPath = await _settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (File.Exists(physicalPath))
                {
                    try { File.Delete(physicalPath); }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[MAINTENANCE] Error deleting file {physicalPath}: {ex.Message}");
                    }
                }
            }

            foreach (var thumbnail in asset.Thumbnails)
            {
                if (!string.IsNullOrEmpty(thumbnail.FilePath) && File.Exists(thumbnail.FilePath))
                {
                    try { File.Delete(thumbnail.FilePath); }
                    catch { /* best effort */ }
                }
            }

            deleted++;

            if (deleted % 50 == 0 || deleted == total)
                Report(onProgress, $"Eliminados {deleted}/{total} activos de la papelera",
                    total > 0 ? (double)deleted / total * 100 : 100, deleted, deleted);
        }

        _dbContext.Assets.RemoveRange(assets);
        await _dbContext.SaveChangesAsync(ct);

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = $"Papelera vaciada. {deleted} activos eliminados permanentemente.",
            Processed = deleted,
            Affected = deleted
        };
    }

    // ─── Purge missing assets & orphan folders ────────────────────────────────

    public async Task<MaintenanceTaskResult> PurgeMissingAsync(
        bool dryRun,
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        Report(onProgress, "Analizando activos ausentes…", 0, 0, 0);

        // Guardarraíl: si una raíz entera no está disponible (volumen sin montar),
        // todo su contenido parece faltante — omitir esos assets en vez de purgar
        // una biblioteca que simplemente está offline.
        var assetsRoot = _settingsService.GetAssetsPath();
        var assetsRootOnline = Directory.Exists(assetsRoot);
        var offlineLibraryIds = (await _dbContext.ExternalLibraries
            .AsNoTracking()
            .Select(l => new { l.Id, l.Path })
            .ToListAsync(ct))
            .Where(l => !Directory.Exists(l.Path))
            .Select(l => l.Id)
            .ToHashSet();

        var missingAssets = await _dbContext.Assets
            .AsNoTracking()
            .Where(a => a.IsFileMissing)
            .Select(a => new { a.Id, a.FullPath, a.ExternalLibraryId })
            .ToListAsync(ct);

        var purgeIds = new List<Guid>();
        int skippedOffline = 0;
        int healed = 0;
        int processed = 0;
        int totalMissing = missingAssets.Count;

        foreach (var asset in missingAssets)
        {
            ct.ThrowIfCancellationRequested();
            processed++;

            var rootOnline = asset.ExternalLibraryId.HasValue
                ? !offlineLibraryIds.Contains(asset.ExternalLibraryId.Value)
                : assetsRootOnline;
            if (!rootOnline)
            {
                skippedOffline++;
            }
            else
            {
                // Doble comprobación: si el archivo reapareció, sanar el flag en vez de purgar
                var physicalPath = await _settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (File.Exists(physicalPath))
                {
                    if (!dryRun)
                        await _dbContext.Assets
                            .Where(a => a.Id == asset.Id)
                            .ExecuteUpdateAsync(s => s.SetProperty(a => a.IsFileMissing, false), ct);
                    healed++;
                }
                else
                {
                    purgeIds.Add(asset.Id);
                }
            }

            if (processed % 100 == 0 || processed == totalMissing)
                Report(onProgress,
                    $"Analizados {processed}/{totalMissing} activos ausentes — {purgeIds.Count} a purgar",
                    totalMissing > 0 ? (double)processed / totalMissing * 90 : 90, processed, purgeIds.Count);
        }

        if (!dryRun && purgeIds.Count > 0)
        {
            // El borrado en BD cascada a exif, miniaturas, tags, caras, embeddings,
            // entradas de álbum, enlaces compartidos y tareas de enriquecimiento.
            foreach (var chunk in purgeIds.Chunk(500))
            {
                await _dbContext.Assets
                    .Where(a => chunk.Contains(a.Id))
                    .ExecuteDeleteAsync(ct);
            }

            // Miniaturas en disco (best effort)
            foreach (var id in purgeIds)
            {
                var thumbDir = Path.Combine(ThumbnailsBasePath, id.ToString());
                if (Directory.Exists(thumbDir))
                {
                    try { Directory.Delete(thumbDir, recursive: true); }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[MAINTENANCE] Error deleting thumbnail dir {thumbDir}: {ex.Message}");
                    }
                }
            }
        }

        Report(onProgress, "Buscando carpetas huérfanas…", 92, processed, purgeIds.Count);

        // ── Carpetas huérfanas: registro en BD cuyo directorio ya no existe ──

        var folders = await _dbContext.Folders
            .AsNoTracking()
            .Select(f => new { f.Id, f.Path, f.ParentFolderId, f.ExternalLibraryId })
            .ToListAsync(ct);

        // Carpetas que seguirán teniendo assets tras la purga (incluida la papelera).
        // Los assets omitidos por raíz offline también cuentan: protegen su carpeta.
        var foldersWithRemainingAssets = (await _dbContext.Assets
            .Where(a => a.FolderId != null && (
                !a.IsFileMissing
                || (a.ExternalLibraryId == null && !assetsRootOnline)
                || (a.ExternalLibraryId != null && offlineLibraryIds.Contains(a.ExternalLibraryId.Value))))
            .Select(a => a.FolderId!.Value)
            .Distinct()
            .ToListAsync(ct))
            .ToHashSet();

        var candidateIds = new HashSet<Guid>();
        foreach (var folder in folders)
        {
            var rootOnline = folder.ExternalLibraryId.HasValue
                ? !offlineLibraryIds.Contains(folder.ExternalLibraryId.Value)
                : assetsRootOnline;
            if (rootOnline && !PhysicalDirectoryExists(folder.Path, assetsRoot))
                candidateIds.Add(folder.Id);
        }

        var childrenByParent = folders
            .Where(f => f.ParentFolderId.HasValue)
            .GroupBy(f => f.ParentFolderId!.Value)
            .ToDictionary(g => g.Key, g => g.Select(f => f.Id).ToList());

        // Una carpeta es purgable si su directorio falta, no le quedan assets y
        // todas sus subcarpetas también lo son (la FK del padre es Restrict).
        var deletableCache = new Dictionary<Guid, bool>();
        bool IsDeletable(Guid id)
        {
            if (deletableCache.TryGetValue(id, out var cached)) return cached;
            deletableCache[id] = false; // corta ciclos defensivamente
            var deletable = candidateIds.Contains(id)
                            && !foldersWithRemainingAssets.Contains(id)
                            && (!childrenByParent.TryGetValue(id, out var children)
                                || children.All(IsDeletable));
            deletableCache[id] = deletable;
            return deletable;
        }

        var foldersToDelete = folders
            .Where(f => IsDeletable(f.Id))
            .Select(f => new { f.Id, f.Path })
            .ToList();

        if (!dryRun && foldersToDelete.Count > 0)
        {
            // Hijos antes que padres: borrar por profundidad descendente
            foreach (var depthGroup in foldersToDelete
                         .GroupBy(f => f.Path.Count(c => c == '/'))
                         .OrderByDescending(g => g.Key))
            {
                var ids = depthGroup.Select(f => f.Id).ToList();
                await _dbContext.Folders
                    .Where(f => ids.Contains(f.Id))
                    .ExecuteDeleteAsync(ct);
            }
        }

        var extras = new List<string>();
        if (healed > 0)
            extras.Add($"{healed} assets recuperados (el archivo volvió a existir)");
        if (skippedOffline > 0)
            extras.Add($"{skippedOffline} assets omitidos (biblioteca sin montar)");
        var extraNote = extras.Count > 0 ? $" {string.Join(". ", extras)}." : "";

        var message = dryRun
            ? $"Simulación: se purgarían {purgeIds.Count} assets y {foldersToDelete.Count} carpetas huérfanas.{extraNote}"
            : $"Purgados {purgeIds.Count} assets y {foldersToDelete.Count} carpetas huérfanas.{extraNote}";

        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = missingAssets.Count + folders.Count,
            Affected = purgeIds.Count + foldersToDelete.Count
        };
    }

    // Equivalente a SettingsService.ResolvePhysicalPathAsync pero para directorios:
    // resuelve la raíz interna /assets y prueba la normalización Unicode opuesta
    // (NFC ↔ NFD) en rutas externas.
    private static bool PhysicalDirectoryExists(string dbPath, string assetsRoot)
    {
        if (string.IsNullOrEmpty(dbPath)) return false;

        if (dbPath.Equals("/assets", StringComparison.OrdinalIgnoreCase))
            return Directory.Exists(assetsRoot);

        if (dbPath.StartsWith("/assets/", StringComparison.OrdinalIgnoreCase))
        {
            var relativePath = dbPath.Substring("/assets/".Length);
            return Directory.Exists(Path.Combine(assetsRoot, relativePath.Replace('/', Path.DirectorySeparatorChar)));
        }

        if (Directory.Exists(dbPath)) return true;

        var nfcPath = dbPath.Normalize(NormalizationForm.FormC);
        if (nfcPath != dbPath && Directory.Exists(nfcPath)) return true;

        var nfdPath = dbPath.Normalize(NormalizationForm.FormD);
        return nfdPath != dbPath && Directory.Exists(nfdPath);
    }
}
