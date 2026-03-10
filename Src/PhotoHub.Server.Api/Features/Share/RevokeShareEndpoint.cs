using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;

namespace PhotoHub.Server.Api.Features.Share;

public class RevokeShareEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapDelete("/api/share/{token}", Handle)
            .WithName("RevokeShareLink")
            .WithTags("Share")
            .WithDescription("Revokes a public share link")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] string token,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        var link = await dbContext.SharedLinks.FirstOrDefaultAsync(l => l.Token == token, ct);
        if (link == null) return Results.NotFound();

        var isAdmin = user.IsInRole("Admin");
        if (!isAdmin && link.CreatedById != userId) return Results.Forbid();

        dbContext.SharedLinks.Remove(link);
        await dbContext.SaveChangesAsync(ct);

        return Results.NoContent();
    }
}
