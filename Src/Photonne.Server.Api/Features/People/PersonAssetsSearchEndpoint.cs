using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.People;

public record PersonAssetDto(
    Guid Id,
    string FileName,
    string Type,
    DateTime FileCreatedAt,
    bool HasThumbnails,
    string? DominantColor);

/// <summary>Lists assets that contain at least one face linked to the given person.
/// Returns enough fields to populate a TimelineItem so the SPA can reuse AssetCard.</summary>
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

        // Distinct asset ids that have at least one non-rejected face for this person.
        var assetIdsQuery = db.Faces.AsNoTracking()
            .Where(f => f.PersonId == personId
                        && !f.IsRejected
                        && f.Asset.OwnerId == userId
                        && f.Asset.DeletedAt == null
                        && !f.Asset.IsFileMissing)
            .Select(f => f.AssetId)
            .Distinct();

        var total = await assetIdsQuery.CountAsync(ct);

        var pagedIds = await assetIdsQuery
            .Take(int.MaxValue)
            .ToListAsync(ct);

        // Fetch the full asset rows for the page (ordered by FileCreatedAt desc).
        var page = await db.Assets.AsNoTracking()
            .Where(a => pagedIds.Contains(a.Id))
            .OrderByDescending(a => a.FileCreatedAt)
            .Skip(offset ?? 0)
            .Take(Math.Clamp(limit ?? 50, 1, 200))
            .Select(a => new PersonAssetDto(
                a.Id,
                a.FileName,
                a.Type == AssetType.Image ? "Image" : "Video",
                a.FileCreatedAt,
                a.Thumbnails.Any(),
                a.Thumbnails
                    .Where(t => t.Size == ThumbnailSize.Small)
                    .Select(t => t.DominantColor)
                    .FirstOrDefault()))
            .ToListAsync(ct);

        return Results.Ok(new { total, items = page });
    }
}
