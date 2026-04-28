using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Objects;

public record DetectedObjectDto(
    Guid Id,
    string Label,
    int ClassId,
    float Confidence,
    float BoundingBoxX,
    float BoundingBoxY,
    float BoundingBoxW,
    float BoundingBoxH);

public class AssetObjectsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/objects", Handle)
            .WithName("GetAssetObjects")
            .WithTags("Objects")
            .WithDescription("Lists every ML-detected object on a single asset.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid assetId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();

        // Confirm ownership before disclosing detections — without this an
        // authenticated user could probe arbitrary asset ids.
        var owns = await db.Assets.AsNoTracking()
            .AnyAsync(a => a.Id == assetId && a.OwnerId == userId, ct);
        if (!owns) return Results.NotFound();

        var objects = await db.ObjectDetections.AsNoTracking()
            .Where(o => o.AssetId == assetId)
            .OrderByDescending(o => o.Confidence)
            .Select(o => new DetectedObjectDto(
                o.Id,
                o.Label,
                o.ClassId,
                o.Confidence,
                o.BoundingBoxX,
                o.BoundingBoxY,
                o.BoundingBoxW,
                o.BoundingBoxH))
            .ToListAsync(ct);

        return Results.Ok(objects);
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}
