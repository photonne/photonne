using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.People;

public record PersonAssetDto(Guid AssetId, string FileName, DateTime FileCreatedAt);

/// <summary>Lists assets that contain at least one face linked to the given person.</summary>
public class PersonAssetsSearchEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/search/people/{personId:guid}/assets", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid personId,
        [FromQuery] int? limit,
        [FromQuery] int? offset,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var person = await db.People.AsNoTracking()
            .FirstOrDefaultAsync(p => p.Id == personId && p.OwnerId == userId, ct);
        if (person == null) return Results.NotFound();

        var q = db.Faces.AsNoTracking()
            .Where(f => f.PersonId == personId && !f.IsRejected && f.Asset.OwnerId == userId)
            .Select(f => new { f.AssetId, f.Asset.FileName, f.Asset.FileCreatedAt })
            .Distinct();

        var total = await q.CountAsync(ct);
        var page = await q
            .OrderByDescending(x => x.FileCreatedAt)
            .Skip(offset ?? 0)
            .Take(Math.Clamp(limit ?? 50, 1, 200))
            .Select(x => new PersonAssetDto(x.AssetId, x.FileName, x.FileCreatedAt))
            .ToListAsync(ct);

        return Results.Ok(new { total, items = page });
    }
}
