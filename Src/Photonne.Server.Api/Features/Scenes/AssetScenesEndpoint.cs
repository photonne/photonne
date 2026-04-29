using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Scenes;

public record ClassifiedSceneDto(
    Guid Id,
    string Label,
    int ClassId,
    float Confidence,
    int Rank);

public class AssetScenesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/scenes", Handle)
            .WithName("GetAssetScenes")
            .WithTags("Scenes")
            .WithDescription("Lists every ML-classified scene on a single asset, ordered by rank.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid assetId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();

        // Confirm ownership before disclosing predictions — without this an
        // authenticated user could probe arbitrary asset ids.
        var owns = await db.Assets.AsNoTracking()
            .AnyAsync(a => a.Id == assetId && a.OwnerId == userId, ct);
        if (!owns) return Results.NotFound();

        var scenes = await db.AssetClassifiedScenes.AsNoTracking()
            .Where(s => s.AssetId == assetId)
            .OrderBy(s => s.Rank)
            .Select(s => new ClassifiedSceneDto(
                s.Id,
                s.Label,
                s.ClassId,
                s.Confidence,
                s.Rank))
            .ToListAsync(ct);

        return Results.Ok(scenes);
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}
