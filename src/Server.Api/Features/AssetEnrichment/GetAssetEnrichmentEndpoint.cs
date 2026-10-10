using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.AssetEnrichment;

/// <summary>
/// Returns the full enrichment-task breakdown for one asset so the mobile
/// client can render per-task chips (Exif ✓ / Thumbs ✗ ⟳ Retry / ML pending).
/// Owner-only.
/// </summary>
public class GetAssetEnrichmentEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{id:guid}/enrichment", Handle)
            .WithName("GetAssetEnrichment")
            .WithTags("Assets")
            .WithDescription("Lists the enrichment tasks (EXIF / thumbnails / media-recognition / ML) for an asset with their current status.")
            .RequireAuthorization();
    }

    private sealed record EnrichmentTaskDto(
        string TaskType,
        string Status,
        string? ErrorMessage,
        int AttemptCount,
        DateTime CreatedAt,
        DateTime? StartedAt,
        DateTime? CompletedAt,
        DateTime? NextRetryAt);

    private sealed record AssetEnrichmentResponse(
        Guid AssetId,
        string FileName,
        IReadOnlyList<EnrichmentTaskDto> Tasks);

    private async Task<Results<Ok<AssetEnrichmentResponse>, NotFound<ApiError>, ForbidHttpResult, UnauthorizedHttpResult>> Handle(
        Guid id,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var asset = await dbContext.Assets
            .AsNoTracking()
            .Where(a => a.Id == id && a.DeletedAt == null)
            .Select(a => new { a.Id, a.FileName, a.OwnerId })
            .FirstOrDefaultAsync(cancellationToken);
        if (asset == null) return TypedResults.NotFound(new ApiError($"Asset {id} not found", "asset_not_found"));
        if (asset.OwnerId != userId) return TypedResults.Forbid();

        var tasks = await dbContext.AssetEnrichmentTasks
            .AsNoTracking()
            .Where(t => t.AssetId == id)
            .OrderBy(t => t.TaskType)
            .Select(t => new EnrichmentTaskDto(
                t.TaskType.ToString(),
                t.Status.ToString(),
                t.ErrorMessage,
                t.AttemptCount,
                t.CreatedAt,
                t.StartedAt,
                t.CompletedAt,
                t.NextRetryAt))
            .ToListAsync(cancellationToken);

        return TypedResults.Ok(new AssetEnrichmentResponse(asset.Id, asset.FileName, tasks));
    }
}
