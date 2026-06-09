using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Objects;

public record ObjectLabelDto(string Label, int AssetCount, string? CoverAssetId);

/// <summary>
/// Returns the distinct object labels detected across the caller's assets,
/// each with the number of distinct assets that contain it. Powers UI
/// facets, autocomplete, and the "browse by tag" affordance.
/// </summary>
public class ObjectLabelsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/objects/labels", Handle)
            .WithName("ListObjectLabels")
            .WithTags("Objects")
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

        // EF Core 9 + Npgsql refuses to translate a GroupBy that follows
        // .Select(...).Distinct(), so we drop down to SqlQueryRaw to keep the
        // work server-side. Beyond the per-label count, we also pick a
        // representative cover asset: of the 30 most recently captured assets
        // carrying the label, the one with the highest detection confidence.
        // This keeps the cover prototypical yet refreshes as new photos arrive,
        // and stays deterministic between calls. The (Label, AssetId) set is
        // pre-aggregated once (MAX confidence) and reused for both the count
        // and the cover pick.
        var rows = await db.Database.SqlQueryRaw<LabelCountRow>(
                """
                WITH per_asset AS (
                    SELECT o."Label"          AS label,
                           o."AssetId"        AS asset_id,
                           MAX(o."Confidence") AS score,
                           a."CapturedAt"     AS captured_at
                    FROM "AssetDetectedObjects" o
                    JOIN "Assets" a ON a."Id" = o."AssetId"
                    WHERE a."OwnerId" = {0}
                      AND a."DeletedAt" IS NULL
                      AND a."IsFileMissing" = FALSE
                      AND a."IsArchived" = FALSE
                      AND o."Label" ILIKE {1}
                    GROUP BY o."Label", o."AssetId", a."CapturedAt"
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
            .Select(r => new ObjectLabelDto(r.Label, r.AssetCount, r.CoverAssetId))
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
