using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// The one definition of "this asset has a problem", shared by the failures
/// registry and by the "N con errores" of every Run Tasks row. They used to be
/// two queries that drifted: the Run Tasks one counted ANY Failed row, so a
/// photo that failed once and was later reprocessed fine kept counting forever
/// (the normal enqueue mints a new row and leaves the old Failed one behind),
/// and so did photos already in the trash. The admin tapped "37 con errores"
/// and landed on a chip that said 12.
/// </summary>
public static class EnrichmentFailureQueries
{
    /// <summary>Latest Failed/Suppressed rows over live assets. A row is an
    /// "open problem" only while no newer attempt exists for the same
    /// (asset, type) — a later Completed/Pending row supersedes it.</summary>
    public static IQueryable<AssetEnrichmentTask> OpenProblems(ApplicationDbContext db) =>
        db.AssetEnrichmentTasks
            .Where(t => t.Status == EnrichmentStatus.Failed || t.Status == EnrichmentStatus.Suppressed)
            .Where(t => t.Asset.DeletedAt == null)
            .Where(t => !db.AssetEnrichmentTasks.Any(n =>
                n.AssetId == t.AssetId &&
                n.TaskType == t.TaskType &&
                n.CreatedAt > t.CreatedAt));

    /// <summary>The open problems that are waiting for somebody: out of
    /// attempts, and not dismissed. This is the number — a row with a retry
    /// still scheduled is the queue's business, and a Suppressed one is a
    /// decision already taken.</summary>
    public static IQueryable<AssetEnrichmentTask> Definitive(this IQueryable<AssetEnrichmentTask> open) =>
        open.Where(t => t.Status == EnrichmentStatus.Failed && t.NextRetryAt == null);

    /// <summary>Still Failed but with a retry on the calendar.</summary>
    public static IQueryable<AssetEnrichmentTask> Retrying(this IQueryable<AssetEnrichmentTask> open) =>
        open.Where(t => t.Status == EnrichmentStatus.Failed && t.NextRetryAt != null);
}
