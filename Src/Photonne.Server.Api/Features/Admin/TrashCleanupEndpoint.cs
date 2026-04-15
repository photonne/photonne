using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

public class TrashStatsResponse
{
    public int TotalItems { get; set; }
    public long TotalBytes { get; set; }
    public int ExpiredItems { get; set; }   // older than retention days
    public int RetentionDays { get; set; }  // 0 = keep forever
    public int MaxQuotaMb { get; set; }     // per-user quota (0 = unlimited)
    public int OverQuotaUsers { get; set; } // users whose trash exceeds the quota
    public long OverQuotaBytes { get; set; } // total excess bytes across all users over quota
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
        var maxQuotaMb    = await GetMaxQuotaMbAsync(settingsService);

        var trashedAssets = await dbContext.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt != null)
            .Select(a => new { a.OwnerId, a.DeletedAt, a.FileSize })
            .ToListAsync(ct);

        var cutoff = retentionDays > 0
            ? DateTime.UtcNow.AddDays(-retentionDays)
            : (DateTime?)null;

        var overQuotaUsers = 0;
        var overQuotaBytes = 0L;
        if (maxQuotaMb > 0)
        {
            var quotaBytes = (long)maxQuotaMb * 1024L * 1024L;
            foreach (var group in trashedAssets.GroupBy(a => a.OwnerId))
            {
                var used = group.Sum(a => a.FileSize);
                if (used > quotaBytes)
                {
                    overQuotaUsers++;
                    overQuotaBytes += used - quotaBytes;
                }
            }
        }

        return Results.Ok(new TrashStatsResponse
        {
            TotalItems  = trashedAssets.Count,
            TotalBytes  = trashedAssets.Sum(a => a.FileSize),
            ExpiredItems = cutoff.HasValue
                ? trashedAssets.Count(a => a.DeletedAt < cutoff)
                : 0,
            RetentionDays  = retentionDays,
            MaxQuotaMb     = maxQuotaMb,
            OverQuotaUsers = overQuotaUsers,
            OverQuotaBytes = overQuotaBytes
        });
    }

    // ─── Cleanup expired ──────────────────────────────────────────────────────

    private static async Task<IResult> CleanupExpired(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        CancellationToken ct)
    {
        var retentionDays = await GetRetentionDaysAsync(settingsService);
        var maxQuotaMb    = await GetMaxQuotaMbAsync(settingsService);

        var toDelete = new List<Asset>();

        // ── 1. Elementos expirados por retención ─────────────────────────────
        if (retentionDays > 0)
        {
            var cutoff = DateTime.UtcNow.AddDays(-retentionDays);
            var expired = await dbContext.Assets
                .Include(a => a.Thumbnails)
                .Where(a => a.DeletedAt != null && a.DeletedAt < cutoff)
                .ToListAsync(ct);
            toDelete.AddRange(expired);
        }

        var expiredCount = toDelete.Count;

        // ── 2. Elementos que superan la cuota por usuario ───────────────────
        var quotaEvicted = 0;
        if (maxQuotaMb > 0)
        {
            var quotaBytes = (long)maxQuotaMb * 1024L * 1024L;
            var alreadyMarked = toDelete.Select(a => a.Id).ToHashSet();

            var remainingTrash = await dbContext.Assets
                .Include(a => a.Thumbnails)
                .Where(a => a.DeletedAt != null && !alreadyMarked.Contains(a.Id))
                .ToListAsync(ct);

            foreach (var userGroup in remainingTrash.GroupBy(a => a.OwnerId))
            {
                var used = userGroup.Sum(a => a.FileSize);
                if (used <= quotaBytes) continue;

                // Eliminar los más antiguos hasta bajar de la cuota
                foreach (var asset in userGroup.OrderBy(a => a.DeletedAt))
                {
                    if (used <= quotaBytes) break;
                    toDelete.Add(asset);
                    used -= asset.FileSize;
                    quotaEvicted++;
                }
            }
        }

        if (toDelete.Count == 0)
        {
            var msg = retentionDays <= 0 && maxQuotaMb <= 0
                ? "La retención está configurada como indefinida y no hay cuota. No se eliminó ningún elemento."
                : $"No hay elementos que eliminar (retención: {(retentionDays > 0 ? retentionDays + " días" : "indefinida")}, cuota: {(maxQuotaMb > 0 ? maxQuotaMb + " MB" : "sin límite")}).";
            return Results.Ok(new TrashCleanupResult { Success = true, Message = msg, Deleted = 0 });
        }

        foreach (var asset in toDelete)
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

        dbContext.Assets.RemoveRange(toDelete);
        await dbContext.SaveChangesAsync(ct);

        var parts = new List<string>();
        if (expiredCount > 0) parts.Add($"{expiredCount} por retención");
        if (quotaEvicted > 0) parts.Add($"{quotaEvicted} por cuota");
        var detail = parts.Count > 0 ? $" ({string.Join(", ", parts)})" : "";

        return Results.Ok(new TrashCleanupResult
        {
            Success = true,
            Message = $"Limpieza completada. {toDelete.Count} elemento(s) eliminado(s) permanentemente{detail}.",
            Deleted = toDelete.Count
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static async Task<int> GetRetentionDaysAsync(SettingsService settingsService)
    {
        var raw = await settingsService.GetSettingAsync("TrashSettings.RetentionDays", Guid.Empty, "30");
        return int.TryParse(raw, out var days) ? Math.Max(0, days) : 30;
    }

    private static async Task<int> GetMaxQuotaMbAsync(SettingsService settingsService)
    {
        var raw = await settingsService.GetSettingAsync("TrashSettings.MaxQuotaMb", Guid.Empty, "0");
        return int.TryParse(raw, out var mb) ? Math.Max(0, mb) : 0;
    }
}
