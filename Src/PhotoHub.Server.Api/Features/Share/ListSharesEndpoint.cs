using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;

namespace PhotoHub.Server.Api.Features.Share;

/// <summary>Lists active share links created by the current user for a given asset or album.</summary>
public class ListSharesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/share", Handle)
            .WithName("ListShareLinks")
            .WithTags("Share")
            .WithDescription("Lists active share links for an asset or album")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromQuery] Guid? assetId,
        [FromQuery] Guid? albumId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        if (assetId == null && albumId == null)
            return Results.BadRequest(new { error = "assetId or albumId is required" });

        // Load all links for this asset/album, then filter expired ones in C#
        // to avoid Npgsql DateTime kind issues with timestamp-without-timezone columns
        var allLinks = await dbContext.SharedLinks
            .Where(l => l.CreatedById == userId &&
                        l.AssetId == assetId &&
                        l.AlbumId == albumId)
            .OrderByDescending(l => l.CreatedAt)
            .ToListAsync(ct);

        var now = DateTime.UtcNow;
        var links = allLinks.Where(l => l.ExpiresAt == null || l.ExpiresAt > now).ToList();

        return Results.Ok(links.Select(CreateShareEndpoint.ToResponse));
    }
}
