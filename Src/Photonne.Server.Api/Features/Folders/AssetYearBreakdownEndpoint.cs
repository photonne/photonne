using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Folders;

/// <summary>
/// Given a set of asset ids, returns how they split across capture years
/// (<c>CapturedAt.Year</c>, newest first). Powers the "se repartirán en…" preview
/// under the manual multi-select move picker, where the client can't compute it
/// itself (the timeline item carries file dates, not the EXIF-derived
/// <c>CapturedAt</c> the move buckets by). Scoped to the caller's own assets so
/// it can never reveal capture years of photos they don't own.
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

        var breakdown = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id) && a.OwnerId == userId && a.DeletedAt == null)
            .GroupBy(a => a.CapturedAt.Year)
            .Select(g => new { Year = g.Key, Count = g.Count() })
            .OrderByDescending(g => g.Year)
            .ToListAsync(cancellationToken);

        return Results.Ok(new AssetYearBreakdownResponse
        {
            YearBreakdown = breakdown.Select(g => new YearCount(g.Year, g.Count)).ToList()
        });
    }

    public class AssetYearBreakdownRequest
    {
        public List<Guid> AssetIds { get; set; } = new();
    }

    public class AssetYearBreakdownResponse
    {
        public IReadOnlyList<YearCount> YearBreakdown { get; set; } = [];
    }
}
