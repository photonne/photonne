using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

public class TrashStatsResponse
{
    public int TotalItems { get; set; }
    public long TotalBytes { get; set; }
    public int ExpiredItems { get; set; }   // older than retention days
    public int RetentionDays { get; set; }  // 0 = keep forever
}

public class TrashCleanupResult
{
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
    public int Deleted { get; set; }
}

public class TrashCleanupEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/trash")
            .WithTags("Trash")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("stats", GetStats)
            .WithName("GetTrashStats")
            .WithDescription("Returns aggregate statistics about the global trash.");

        group.MapPost("cleanup-expired", CleanupExpired)
            .WithName("CleanupExpiredTrash")
            .WithDescription("Permanently deletes all trash items older than the configured retention period.");
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    private static async Task<IResult> GetStats(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        CancellationToken ct)
    {
        var retentionDays = await GetRetentionDaysAsync(settingsService);

        var trashedAssets = await dbContext.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt != null)
            .Select(a => new { a.DeletedAt, a.FileSize })
            .ToListAsync(ct);

        var cutoff = retentionDays > 0
            ? DateTime.UtcNow.AddDays(-retentionDays)
            : (DateTime?)null;

        return Results.Ok(new TrashStatsResponse
        {
            TotalItems  = trashedAssets.Count,
            TotalBytes  = trashedAssets.Sum(a => a.FileSize),
            ExpiredItems = cutoff.HasValue
                ? trashedAssets.Count(a => a.DeletedAt < cutoff)
                : 0,
            RetentionDays = retentionDays
        });
    }

    // ─── Cleanup expired ──────────────────────────────────────────────────────

    private static async Task<IResult> CleanupExpired(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        CancellationToken ct)
    {
        var retentionDays = await GetRetentionDaysAsync(settingsService);

        if (retentionDays <= 0)
        {
            return Results.Ok(new TrashCleanupResult
            {
                Success = true,
                Message = "La retención está configurada como indefinida. No se eliminó ningún elemento.",
                Deleted = 0
            });
        }

        var cutoff = DateTime.UtcNow.AddDays(-retentionDays);

        var expired = await dbContext.Assets
            .Include(a => a.Thumbnails)
            .Where(a => a.DeletedAt != null && a.DeletedAt < cutoff)
            .ToListAsync(ct);

        if (!expired.Any())
        {
            return Results.Ok(new TrashCleanupResult
            {
                Success = true,
                Message = $"No hay elementos con más de {retentionDays} días en la papelera.",
                Deleted = 0
            });
        }

        foreach (var asset in expired)
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
                        Console.WriteLine($"[TRASH CLEANUP] Error deleting {physicalPath}: {ex.Message}");
                    }
                }
            }

            foreach (var thumb in asset.Thumbnails)
            {
                if (!string.IsNullOrEmpty(thumb.FilePath) && File.Exists(thumb.FilePath))
                {
                    try { File.Delete(thumb.FilePath); }
                    catch { /* best effort */ }
                }
            }
        }

        dbContext.Assets.RemoveRange(expired);
        await dbContext.SaveChangesAsync(ct);

        return Results.Ok(new TrashCleanupResult
        {
            Success = true,
            Message = $"Limpieza completada. {expired.Count} elemento(s) eliminado(s) permanentemente.",
            Deleted = expired.Count
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static async Task<int> GetRetentionDaysAsync(SettingsService settingsService)
    {
        var raw = await settingsService.GetSettingAsync("TrashSettings.RetentionDays", Guid.Empty, "30");
        return int.TryParse(raw, out var days) ? Math.Max(0, days) : 30;
    }
}
