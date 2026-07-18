using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Folders;

/// <summary>
/// Given a set of asset ids, returns them grouped by capture year
/// (<c>CapturedAt.Year</c>, newest first; ids within a year by capture date desc).
/// Powers the "se repartirán en…" chips and the "Revisar" thumbnail grid under the
/// manual multi-select move picker, where the client can't compute it itself (the
/// timeline item carries file dates, not the EXIF-derived <c>CapturedAt</c> the
/// move buckets by). Scoped to the caller's own assets so it can never reveal
/// capture years of photos they don't own.
/// </summary>
public class AssetYearBreakdownEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/year-breakdown", Handle)
            .WithName("AssetYearBreakdown")
            .WithTags("Assets")
            .WithDescription("Return how the given assets split across capture years")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromBody] AssetYearBreakdownRequest request,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        if (request.AssetIds is null || request.AssetIds.Count == 0)
            return Results.Ok(new AssetYearBreakdownResponse());

        var rows = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id) && a.OwnerId == userId && a.DeletedAt == null)
            .OrderByDescending(a => a.CapturedAt)
            .ThenBy(a => a.Id)
            .Select(a => new { a.Id, Year = a.CapturedAt.Year })
            .ToListAsync(cancellationToken);

        var groups = rows
            .GroupBy(r => r.Year)
            .OrderByDescending(g => g.Key)
            .Select(g => new YearGroup(g.Key, g.Select(r => r.Id).ToList()))
            .ToList();

        return Results.Ok(new AssetYearBreakdownResponse { Groups = groups });
    }

    public class AssetYearBreakdownRequest
    {
        public List<Guid> AssetIds { get; set; } = new();
    }

    public class AssetYearBreakdownResponse
    {
        public IReadOnlyList<YearGroup> Groups { get; set; } = [];
    }
}
