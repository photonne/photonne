using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetEnrichment;

/// <summary>
/// Resets every <see cref="EnrichmentStatus.Failed"/> task of an asset back
/// to <c>Pending</c> and re-enqueues them. Doesn't touch Pending/Processing/
/// Completed rows — only the ones the user can actually see as broken.
/// Owner-only.
/// </summary>
public class RetryAllEnrichmentTasksEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/{id:guid}/enrichment/retry-all", Handle)
            .WithName("RetryAllEnrichmentTasks")
            .WithTags("Assets")
            .WithDescription("Resets all Failed enrichment tasks for one asset back to Pending and re-enqueues them.")
            .RequireAuthorization()
            .RequireRateLimiting("demo-upload");
    }

    private async Task<IResult> Handle(
        Guid id,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IEnrichmentService enrichmentService,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        var ownerId = await dbContext.Assets
            .Where(a => a.Id == id && a.DeletedAt == null)
            .Select(a => (Guid?)a.OwnerId)
            .FirstOrDefaultAsync(cancellationToken);
        if (ownerId == null) return Results.NotFound();
        if (ownerId != userId) return Results.Forbid();

        var failedIds = await dbContext.AssetEnrichmentTasks
            .Where(t => t.AssetId == id && t.Status == EnrichmentStatus.Failed)
            .Select(t => t.Id)
            .ToListAsync(cancellationToken);

        var retried = 0;
        foreach (var taskId in failedIds)
        {
            if (await enrichmentService.ResetAndEnqueueAsync(taskId, cancellationToken))
                retried++;
        }

        return Results.Ok(new { assetId = id, retried });
    }
}
