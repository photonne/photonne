using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetEnrichment;

/// <summary>
/// What every per-asset queue is doing right now, for all task types in one
/// answer: queued, claimed, waiting out a retry, given up.
///
/// It exists because the per-type <c>pending-count</c> endpoints answer this
/// AND "how much of the library is left", and the second half walks the asset
/// table. Run Tasks waited for six of those before lighting a row, so a retry
/// sent from the failures registry had usually finished before the screen could
/// show it had started. Everything here reads the task table through its
/// indexes — cheap enough to ask for first and to poll fast.
///
/// It also covers the types that have no <c>pending-count</c> at all (Exif,
/// Thumbnails): their rows run as sweeps, but a retry from the registry goes
/// through the per-asset queue, where nothing used to report it.
/// </summary>
public class AdminEnrichmentQueueSummaryEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/admin/enrichment/queue-summary", Handle)
            .WithName("AdminEnrichmentQueueSummary")
            .WithTags("Admin")
            .WithDescription("Per task type: jobs queued, being processed, retrying and failed for good.")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    /// <param name="InQueue">Pending + Processing, like <c>pending-count</c>.</param>
    /// <param name="Failed">Same definition as the failures registry — see
    /// <see cref="EnrichmentFailureQueries"/>.</param>
    private sealed record QueueCounts(int InQueue, int Processing, int Retrying, int Failed);

    private sealed record QueueSummaryResponse(IReadOnlyDictionary<string, QueueCounts> Types);

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        CancellationToken ct)
    {
        var live = await db.AssetEnrichmentTasks.AsNoTracking()
            .Where(t => t.Status == EnrichmentStatus.Pending || t.Status == EnrichmentStatus.Processing)
            .GroupBy(t => new { t.TaskType, t.Status })
            .Select(g => new { g.Key.TaskType, g.Key.Status, Count = g.Count() })
            .ToListAsync(ct);

        var problems = await EnrichmentFailureQueries.OpenProblems(db).AsNoTracking()
            .Where(t => t.Status == EnrichmentStatus.Failed)
            .GroupBy(t => new { t.TaskType, Permanent = t.NextRetryAt == null })
            .Select(g => new { g.Key.TaskType, g.Key.Permanent, Count = g.Count() })
            .ToListAsync(ct);

        // Every type, zeros included: the client tells "nothing queued" from
        // "a server that doesn't report this type" by the key being there.
        var types = Enum.GetValues<AssetEnrichmentType>().ToDictionary(
            type => type.ToString(),
            type =>
            {
                var processing = live.Where(l => l.TaskType == type && l.Status == EnrichmentStatus.Processing).Sum(l => l.Count);
                var pending = live.Where(l => l.TaskType == type && l.Status == EnrichmentStatus.Pending).Sum(l => l.Count);
                return new QueueCounts(
                    InQueue: pending + processing,
                    Processing: processing,
                    Retrying: problems.Where(p => p.TaskType == type && !p.Permanent).Sum(p => p.Count),
                    Failed: problems.Where(p => p.TaskType == type && p.Permanent).Sum(p => p.Count));
            });

        return Results.Ok(new QueueSummaryResponse(types));
    }
}
