using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Share;

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

    private static async Task<Results<NoContent, UnauthorizedHttpResult, NotFound<ApiError>, ForbidHttpResult>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] string token,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return TypedResults.Unauthorized();

        var link = await dbContext.SharedLinks.FirstOrDefaultAsync(l => l.Token == token, ct);
        if (link == null) return TypedResults.NotFound(new ApiError("Share link not found", "share_link_not_found"));

        if (link.CreatedById != userId) return TypedResults.Forbid();

        dbContext.SharedLinks.Remove(link);
        await dbContext.SaveChangesAsync(ct);

        return TypedResults.NoContent();
    }
}
