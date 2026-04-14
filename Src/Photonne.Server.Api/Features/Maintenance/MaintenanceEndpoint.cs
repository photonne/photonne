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
        int markedOffline = 0;

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
                markedOffline++;
            }
        }

        var message = markedOffline == 0
            ? $"Revisados {processed} activos. Todos los archivos están presentes."
            : $"Revisados {processed} activos. {markedOffline} marcados como fuera de línea.";

        return Results.Ok(new MaintenanceTaskResult
        {
            Success = true,
            Message = message,
            Processed = processed,
            Affected = markedOffline
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
}
