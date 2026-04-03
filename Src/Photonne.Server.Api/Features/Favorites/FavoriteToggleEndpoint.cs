using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Favorites;

public class FavoriteToggleEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/{assetId}/favorite", Handle)
            .WithName("ToggleFavorite")
            .WithTags("Assets")
            .WithDescription("Toggles the favorite state of an asset")
            .RequireAuthorization();
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid assetId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var asset = await dbContext.Assets
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, cancellationToken);

        if (asset == null)
            return Results.NotFound(new { error = $"Asset {assetId} not found" });

        asset.IsFavorite = !asset.IsFavorite;
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.Ok(new { isFavorite = asset.IsFavorite });
    }
}
