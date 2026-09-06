using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Features.Maintenance;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>
/// Serves the last snapshot persisted by the indexing-coverage maintenance
/// task. Read-only and cheap (one Settings row) so the stats screen can show
/// it without triggering the multi-minute disk walk.
/// </summary>
public class IndexingCoverageEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/admin/indexing-coverage", Handle)
            .WithName("AdminIndexingCoverage")
            .WithTags("Admin")
            .WithDescription("Returns the last indexing-coverage verification result, or hasResult=false when the task never ran.")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private sealed record IndexingCoverageResponse(
        bool HasResult,
        DateTime? VerifiedAtUtc,
        int TotalFiles,
        int Indexed,
        int Unsupported,
        int Unindexed,
        IReadOnlyList<string> UnindexedPaths,
        bool UnindexedTruncated,
        int OfflineLibraries);

    private async Task<IResult> Handle(
        [FromServices] MaintenanceService maintenanceService,
        CancellationToken cancellationToken)
    {
        var last = await maintenanceService.GetLastIndexingCoverageAsync(cancellationToken);
        if (last == null)
        {
            return Results.Ok(new IndexingCoverageResponse(
                false, null, 0, 0, 0, 0, Array.Empty<string>(), false, 0));
        }

        return Results.Ok(new IndexingCoverageResponse(
            true, last.VerifiedAtUtc, last.TotalFiles, last.Indexed, last.Unsupported,
            last.Unindexed, last.UnindexedPaths, last.UnindexedTruncated, last.OfflineLibraries));
    }
}
