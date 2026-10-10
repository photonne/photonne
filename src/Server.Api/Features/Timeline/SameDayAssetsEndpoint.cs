using Microsoft.AspNetCore.Http.HttpResults;
using Photonne.Server.Api.Shared.Dtos;
using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.People;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Other visible assets captured on the same calendar day as the given asset
/// (the asset itself excluded), newest first. Powers the "more from this day"
/// block in the asset-detail info panel. Reuses <see cref="PersonAssetDto"/>
/// and the <c>{ total, items }</c> envelope so the client shares the
/// PersonAssetsPage deserialization with the people search.
/// </summary>
public class SameDayAssetsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/same-day", Handle)
            .WithName("GetSameDayAssets")
            .WithTags("Assets")
            .WithDescription("Returns other visible assets captured on the same calendar day as the given asset")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<SameDayAssetsResponse>, NotFound<ApiError>, UnauthorizedHttpResult>> Handle(
        [FromRoute] Guid assetId,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        [FromQuery] int? limit,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        var capturedAt = await dbContext.Assets.AsNoTracking()
            .Where(a => a.Id == assetId)
            .Select(a => (DateTime?)a.CapturedAt)
            .FirstOrDefaultAsync(cancellationToken);
        if (capturedAt == null) return TypedResults.NotFound(new ApiError($"Asset {assetId} not found", "asset_not_found"));

        // Half-open day range on the stored UTC instant — lets Postgres
        // walk the CapturedAt index instead of computing date parts.
        var dayStart = DateTime.SpecifyKind(capturedAt.Value.Date, DateTimeKind.Utc);
        var dayEnd = dayStart.AddDays(1);

        var userRootPath = $"/assets/users/{username}";
        var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);

        var query = TimelineQuery.VisibleAssets(dbContext, allowedIds)
            .Where(a => a.Id != assetId
                     && a.CapturedAt >= dayStart && a.CapturedAt < dayEnd);

        var total = await query.CountAsync(cancellationToken);

        var items = await query
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .Take(Math.Clamp(limit ?? 12, 1, 100))
            .Select(a => new PersonAssetDto(
                a.Id,
                a.FileName,
                a.Type == AssetType.Image ? "Image" : "Video",
                a.CapturedAt,
                a.Thumbnails.Any(),
                a.Thumbnails
                    .Where(t => t.Size == ThumbnailSize.Small)
                    .Select(t => t.DominantColor)
                    .FirstOrDefault()))
            .ToListAsync(cancellationToken);

        return TypedResults.Ok(new SameDayAssetsResponse(total, items));
    }
}

public sealed record SameDayAssetsResponse(int Total, List<PersonAssetDto> Items);
