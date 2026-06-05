using System.Text;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
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
    private const string ThumbnailsBasePath = "/data/thumbnails";

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Maintenance")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("orphan-thumbnails", CleanOrphanThumbnails)
            .WithName("CleanOrphanThumbnails")
            .WithDescription("Deletes thumbnail files on disk that have no matching asset in the database.");

        group.MapPost("missing-files", MarkMissingFiles)
            .WithName("MarkMissingFiles")
            .WithDescription("Marks as offline any asset whose physical file no longer exists on disk.");

        group.MapPost("recalculate-sizes", RecalculateSizes)
            .WithName("RecalculateSizes")
            .WithDescription("Recalculates the stored file size for every asset by reading the actual file on disk.");

        group.MapPost("empty-trash", EmptyGlobalTrash)
            .WithName("EmptyGlobalTrash")
            .WithDescription("Permanently deletes all assets currently in the trash for all users.");

        group.MapPost("purge-missing", PurgeMissing)
            .WithName("PurgeMissingAssets")
            .WithDescription("Permanently deletes assets marked as missing and folder records whose physical directory no longer exists. Pass ?dryRun=true to preview without deleting.");
    }

    // ─── Orphan thumbnails ────────────────────────────────────────────────────

    private static async Task<IResult> CleanOrphanThumbnails(
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken ct)
    {
        if (!Directory.Exists(ThumbnailsBasePath))
            return Results.Ok(new MaintenanceTaskResult
            {
                Success = true,
                Message = "El directorio de miniaturas no existe. Nada que limpiar.",
                Processed = 0,
                Affected = 0
            });

        // Collect all asset IDs that have thumbnail records in the database
        var knownAssetIds = await dbContext.AssetThumbnails
            .AsNoTracking()
            .Select(t => t.AssetId)
            .Distinct()
            .ToHashSetAsync(ct);

        // Also collect all asset IDs that exist in the Assets table
        var existingAssetIds = await dbContext.Assets
            .AsNoTracking()
            .Select(a => a.Id)
            .ToHashSetAsync(ct);

        int processed = 0;
        int deleted = 0;
        var errors = 0;

        // Each subdirectory in the thumbnails folder is named after an asset GUID
        foreach (var dir in Directory.EnumerateDirectories(ThumbnailsBasePath))
        {
            ct.ThrowIfCancellationRequested();
            processed++;

            var dirName = Path.GetFileName(dir);
            if (!Guid.TryParse(dirName, out var assetId))
                continue;

            // Orphan: neither in Assets table nor referenced by any AssetThumbnail record
            if (!existingAssetIds.Contains(assetId) && !knownAssetIds.Contains(assetId))
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
        }

        var message = deleted == 0
            ? $"Revisados {processed} directorios. No se encontraron miniaturas huérfanas."
            : $"Revisados {processed} directorios. {deleted} eliminados.{(errors > 0 ? $" {errors} errores." : "")}";

        return Results.Ok(new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = deleted
        });
    }

    // ─── Mark missing files ───────────────────────────────────────────────────

    private static async Task<IResult> MarkMissingFiles(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        CancellationToken ct)
    {
        var assets = await dbContext.Assets
            .Where(a => a.DeletedAt == null && !a.IsFileMissing)
            .Select(a => new { a.Id, a.FullPath })
            .ToListAsync(ct);

        int processed = 0;
        int markedMissing = 0;

        foreach (var asset in assets)
        {
            ct.ThrowIfCancellationRequested();
            processed++;

            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (!File.Exists(physicalPath))
            {
                await dbContext.Assets
                    .Where(a => a.Id == asset.Id)
                    .ExecuteUpdateAsync(s => s.SetProperty(a => a.IsFileMissing, true), ct);
                markedMissing++;
            }
        }

        var message = markedMissing == 0
            ? $"Revisados {processed} activos. Todos los archivos están presentes."
            : $"Revisados {processed} activos. {markedMissing} marcados como faltantes.";

        return Results.Ok(new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = markedMissing
        });
    }

    // ─── Recalculate sizes ────────────────────────────────────────────────────

    private static async Task<IResult> RecalculateSizes(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        CancellationToken ct)
    {
        var assets = await dbContext.Assets
            .Where(a => a.DeletedAt == null && !a.IsFileMissing)
            .Select(a => new { a.Id, a.FullPath, a.FileSize })
            .ToListAsync(ct);

        int processed = 0;
        int updated = 0;

        foreach (var asset in assets)
        {
            ct.ThrowIfCancellationRequested();
            processed++;

            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (!File.Exists(physicalPath))
                continue;

            var actualSize = new FileInfo(physicalPath).Length;
            if (actualSize != asset.FileSize)
            {
                await dbContext.Assets
                    .Where(a => a.Id == asset.Id)
                    .ExecuteUpdateAsync(s => s.SetProperty(a => a.FileSize, actualSize), ct);
                updated++;
            }
        }

        var message = updated == 0
            ? $"Revisados {processed} activos. Todos los tamaños son correctos."
            : $"Revisados {processed} activos. {updated} actualizados.";

        return Results.Ok(new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = updated
        });
    }

    // ─── Empty global trash ───────────────────────────────────────────────────

    private static async Task<IResult> EmptyGlobalTrash(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        CancellationToken ct)
    {
        var assets = await dbContext.Assets
            .Include(a => a.Thumbnails)
            .Where(a => a.DeletedAt != null)
            .ToListAsync(ct);

        if (!assets.Any())
        {
            return Results.Ok(new MaintenanceTaskResult
            {
                Success = true,
                Message = "La papelera ya está vacía.",
                Processed = 0,
                Affected = 0
            });
        }

        int deleted = 0;

        foreach (var asset in assets)
        {
            ct.ThrowIfCancellationRequested();

            // No borrar archivos físicos de bibliotecas externas — no son propiedad de Photonne
            if (!asset.ExternalLibraryId.HasValue)
            {
                var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
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
        }

        dbContext.Assets.RemoveRange(assets);
        await dbContext.SaveChangesAsync(ct);

        return Results.Ok(new MaintenanceTaskResult
        {
            Success = true,
            Message = $"Papelera vaciada. {deleted} activos eliminados permanentemente.",
            Processed = deleted,
            Affected = deleted
        });
    }

    // ─── Purge missing assets & orphan folders ────────────────────────────────

    private static async Task<IResult> PurgeMissing(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        CancellationToken ct,
        [FromQuery] bool dryRun = false)
    {
        // Guardarraíl: si una raíz entera no está disponible (volumen sin montar),
        // todo su contenido parece faltante — omitir esos assets en vez de purgar
        // una biblioteca que simplemente está offline.
        var assetsRoot = settingsService.GetAssetsPath();
        var assetsRootOnline = Directory.Exists(assetsRoot);
        var offlineLibraryIds = (await dbContext.ExternalLibraries
            .AsNoTracking()
            .Select(l => new { l.Id, l.Path })
            .ToListAsync(ct))
            .Where(l => !Directory.Exists(l.Path))
            .Select(l => l.Id)
            .ToHashSet();

        var missingAssets = await dbContext.Assets
            .AsNoTracking()
            .Where(a => a.IsFileMissing)
            .Select(a => new { a.Id, a.FullPath, a.ExternalLibraryId })
            .ToListAsync(ct);

        var purgeIds = new List<Guid>();
        int skippedOffline = 0;
        int healed = 0;

        foreach (var asset in missingAssets)
        {
            ct.ThrowIfCancellationRequested();

            var rootOnline = asset.ExternalLibraryId.HasValue
                ? !offlineLibraryIds.Contains(asset.ExternalLibraryId.Value)
                : assetsRootOnline;
            if (!rootOnline)
            {
                skippedOffline++;
                continue;
            }

            // Doble comprobación: si el archivo reapareció, sanar el flag en vez de purgar
            var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
            if (File.Exists(physicalPath))
            {
                if (!dryRun)
                    await dbContext.Assets
                        .Where(a => a.Id == asset.Id)
                        .ExecuteUpdateAsync(s => s.SetProperty(a => a.IsFileMissing, false), ct);
                healed++;
                continue;
            }

            purgeIds.Add(asset.Id);
        }

        if (!dryRun && purgeIds.Count > 0)
        {
            // El borrado en BD cascada a exif, miniaturas, tags, caras, embeddings,
            // entradas de álbum, enlaces compartidos y tareas de enriquecimiento.
            foreach (var chunk in purgeIds.Chunk(500))
            {
                await dbContext.Assets
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

        // ── Carpetas huérfanas: registro en BD cuyo directorio ya no existe ──

        var folders = await dbContext.Folders
            .AsNoTracking()
            .Select(f => new { f.Id, f.Path, f.ParentFolderId, f.ExternalLibraryId })
            .ToListAsync(ct);

        // Carpetas que seguirán teniendo assets tras la purga (incluida la papelera).
        // Los assets omitidos por raíz offline también cuentan: protegen su carpeta.
        var foldersWithRemainingAssets = (await dbContext.Assets
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
                await dbContext.Folders
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

        return Results.Ok(new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = missingAssets.Count + folders.Count,
            Affected = purgeIds.Count + foldersToDelete.Count
        });
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
