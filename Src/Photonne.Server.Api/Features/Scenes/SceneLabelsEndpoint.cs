using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Scenes;

public record SceneLabelDto(string Label, int AssetCount, string? CoverAssetId);

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
        // El patrón va plegado (minúsculas + sin acentos) y la columna se pliega en
        // el propio SQL, para que "cafe" saque "café".
        var likePattern = string.IsNullOrWhiteSpace(q) ? "%" : SearchText.ContainsPattern(q);

        // Same SqlQueryRaw rationale as ObjectLabelsEndpoint: EF Core 9 +
        // Npgsql refuses to translate GroupBy chained after Distinct(), so we
        // drop down to raw SQL to keep the COUNT(DISTINCT) server-side. The
        // cover pick mirrors objects: of the 30 most recently captured assets
        // carrying the label, the one with the highest classification
        // confidence — prototypical, refreshing, and deterministic.
        var rows = await db.Database.SqlQueryRaw<LabelCountRow>(
                """
                WITH per_asset AS (
                    SELECT s."Label"           AS label,
                           s."AssetId"         AS asset_id,
                           MAX(s."Confidence") AS score,
                           a."CapturedAt"      AS captured_at
                    FROM "AssetClassifiedScenes" s
                    JOIN "Assets" a ON a."Id" = s."AssetId"
                    WHERE a."OwnerId" = {0}
                      AND a."DeletedAt" IS NULL
                      AND a."IsFileMissing" = FALSE
                      AND a."IsArchived" = FALSE
                      AND photonne_unaccent(s."Label") ILIKE {1}
                    GROUP BY s."Label", s."AssetId", a."CapturedAt"
                ),
                recent AS (
                    SELECT label, asset_id, score,
                           ROW_NUMBER() OVER (
                               PARTITION BY label
                               ORDER BY captured_at DESC, asset_id DESC
                           ) AS recency_rank
                    FROM per_asset
                ),
                cover AS (
                    SELECT DISTINCT ON (label) label, asset_id
                    FROM recent
                    WHERE recency_rank <= 30
                    ORDER BY label, score DESC, asset_id DESC
                )
                SELECT p.label                         AS "Label",
                       COUNT(DISTINCT p.asset_id)::int AS "AssetCount",
                       c.asset_id::text                AS "CoverAssetId"
                FROM per_asset p
                JOIN cover c ON c.label = p.label
                GROUP BY p.label, c.asset_id
                ORDER BY "AssetCount" DESC, p.label ASC
                LIMIT {2}
                """,
                userId, likePattern, take)
            .ToListAsync(ct);

        var labels = rows
            .Select(r => new SceneLabelDto(r.Label, r.AssetCount, r.CoverAssetId))
            .ToList();

        return Results.Ok(labels);
    }

    // Keyless row type returned by SqlQueryRaw. Property names match the SQL
    // aliases so EF can bind by name without an explicit shaper.
    private sealed record LabelCountRow(string Label, int AssetCount, string? CoverAssetId);

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}
