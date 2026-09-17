using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Admin;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Features.AssetEnrichment;

/// <summary>
/// Forces a single enrichment task back to <see cref="EnrichmentStatus.Pending"/>
/// and re-enqueues it. Works on Failed rows (clears the backoff bookkeeping)
/// and also on already-Completed rows when the user wants to force a rerun.
/// Owner-only.
/// </summary>
public class RetryEnrichmentTaskEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/{id:guid}/enrichment/retry", Handle)
            .WithName("RetryEnrichmentTask")
            .WithTags("Assets")
            .WithDescription("Resets one enrichment task back to Pending and re-enqueues it for the worker.")
            .RequireAuthorization()
            .RequireRateLimiting("demo-upload");
    }

    private async Task<IResult> Handle(
        Guid id,
        [FromQuery] string taskType,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IEnrichmentService enrichmentService,
        [FromServices] MlEnablement enablement,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        if (!Enum.TryParse<AssetEnrichmentType>(taskType, ignoreCase: true, out var parsedType))
        {
            return Results.BadRequest(new
            {
                error = $"Unknown task type '{taskType}'. Valid: {string.Join(", ", Enum.GetNames<AssetEnrichmentType>())}"
            });
        }

        // Same refusal as the admin backfill: a disabled model's worker
        // completes the job without recording anything, so the row would sit
        // on "en cola" and then quietly go back to "nunca". Saying no, with
        // the reason, is the only honest answer.
        if (!await enablement.IsEnabledAsync(parsedType))
        {
            return Results.Json(
                new { error = $"El análisis «{MlBackfillRunner.JobTypeLabel(parsedType)}» está desactivado en Ajustes." },
                statusCode: StatusCodes.Status409Conflict);
        }

        var ownerId = await dbContext.Assets
            .Where(a => a.Id == id && a.DeletedAt == null)
            .Select(a => (Guid?)a.OwnerId)
            .FirstOrDefaultAsync(cancellationToken);
        if (ownerId == null) return Results.NotFound();
        if (ownerId != userId) return Results.Forbid();

        var taskId = await dbContext.AssetEnrichmentTasks
            .Where(t => t.AssetId == id && t.TaskType == parsedType)
            .OrderByDescending(t => t.CreatedAt)
            .Select(t => (Guid?)t.Id)
            .FirstOrDefaultAsync(cancellationToken);

        if (taskId == null || taskId == Guid.Empty)
        {
            // No row yet for this type — create one in Pending and enqueue. Lets the
            // client ask for "run Exif on this asset" even if it was never enqueued.
            await enrichmentService.EnqueueAsync(id, parsedType, cancellationToken);
            return Results.Ok(new { assetId = id, taskType = parsedType.ToString(), status = "Pending" });
        }

        var ok = await enrichmentService.ResetAndEnqueueAsync(taskId.Value, cancellationToken);
        if (!ok) return Results.NotFound();

        return Results.Ok(new { assetId = id, taskType = parsedType.ToString(), status = "Pending" });
    }
}
