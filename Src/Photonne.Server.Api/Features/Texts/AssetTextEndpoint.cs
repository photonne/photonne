using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Texts;

public record RecognizedTextLineDto(
    Guid Id,
    string Text,
    float Confidence,
    float BBoxX,
    float BBoxY,
    float BBoxWidth,
    float BBoxHeight,
    int LineIndex);

public class AssetTextEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{assetId:guid}/text", Handle)
            .WithName("GetAssetText")
            .WithTags("Texts")
            .WithDescription("Lists every OCR-extracted text line on a single asset, ordered by reading order.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid assetId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();

        // Confirm ownership before disclosing extracted text — without this an
        // authenticated user could probe arbitrary asset ids.
        var owns = await db.Assets.AsNoTracking()
            .AnyAsync(a => a.Id == assetId && a.OwnerId == userId, ct);
        if (!owns) return Results.NotFound();

        var lines = await db.AssetRecognizedTextLines.AsNoTracking()
            .Where(t => t.AssetId == assetId)
            .OrderBy(t => t.LineIndex)
            .Select(t => new RecognizedTextLineDto(
                t.Id,
                t.Text,
                t.Confidence,
                t.BBoxX,
                t.BBoxY,
                t.BBoxWidth,
                t.BBoxHeight,
                t.LineIndex))
            .ToListAsync(ct);

        return Results.Ok(lines);
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}
