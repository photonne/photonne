using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Scenes;

public record SceneLabelDto(string Label, int AssetCount);

/// <summary>
/// Returns the distinct scene labels detected across the caller's assets,
/// each with the number of distinct assets that contain it. Powers UI
/// facets, autocomplete, and the "browse by scene" affordance.
/// </summary>
public class SceneLabelsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/scenes/labels", Handle)
            .WithName("ListSceneLabels")
            .WithTags("Scenes")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        [FromQuery] string? q,
        [FromQuery] int? limit,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var take = Math.Clamp(limit ?? 200, 1, 1000);
        // ILIKE '%' matches every label, so we can pass a plain non-null
        // pattern even when the caller didn't supply q — keeps the SQL
        // shape uniform and the parameter list null-free.
        var likePattern = string.IsNullOrWhiteSpace(q) ? "%" : "%" + q.Trim() + "%";

        // Same SqlQueryRaw rationale as ObjectLabelsEndpoint: EF Core 9 +
        // Npgsql refuses to translate GroupBy chained after Distinct(), so we
        // drop down to raw SQL to keep the COUNT(DISTINCT) server-side.
        var rows = await db.Database.SqlQueryRaw<LabelCountRow>(
                """
                SELECT s."Label" AS "Label",
                       COUNT(DISTINCT s."AssetId")::int AS "AssetCount"
                FROM "SceneClassifications" s
                JOIN "Assets" a ON a."Id" = s."AssetId"
                WHERE a."OwnerId" = {0}
                  AND a."DeletedAt" IS NULL
                  AND a."IsFileMissing" = FALSE
                  AND a."IsArchived" = FALSE
                  AND s."Label" ILIKE {1}
                GROUP BY s."Label"
                ORDER BY "AssetCount" DESC, s."Label" ASC
                LIMIT {2}
                """,
                userId, likePattern, take)
            .ToListAsync(ct);

        var labels = rows
            .Select(r => new SceneLabelDto(r.Label, r.AssetCount))
            .ToList();

        return Results.Ok(labels);
    }

    // Keyless row type returned by SqlQueryRaw. Property names match the SQL
    // aliases so EF can bind by name without an explicit shaper.
    private sealed record LabelCountRow(string Label, int AssetCount);

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}
