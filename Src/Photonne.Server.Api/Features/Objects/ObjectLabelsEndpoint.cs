using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Objects;

public record ObjectLabelDto(string Label, int AssetCount);

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
        // .Select(...).Distinct(), regardless of whether the projection target
        // is a record or an anonymous type. The query itself is trivial in SQL:
        // count the distinct asset ids per label. Drop down to SqlQueryRaw so
        // we keep the work server-side and avoid materializing every (label,
        // asset) pair.
        var rows = await db.Database.SqlQueryRaw<LabelCountRow>(
                """
                SELECT o."Label" AS "Label",
                       COUNT(DISTINCT o."AssetId")::int AS "AssetCount"
                FROM "AssetDetectedObjects" o
                JOIN "Assets" a ON a."Id" = o."AssetId"
                WHERE a."OwnerId" = {0}
                  AND a."DeletedAt" IS NULL
                  AND a."IsFileMissing" = FALSE
                  AND a."IsArchived" = FALSE
                  AND o."Label" ILIKE {1}
                GROUP BY o."Label"
                ORDER BY "AssetCount" DESC, o."Label" ASC
                LIMIT {2}
                """,
                userId, likePattern, take)
            .ToListAsync(ct);

        var labels = rows
            .Select(r => new ObjectLabelDto(r.Label, r.AssetCount))
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
