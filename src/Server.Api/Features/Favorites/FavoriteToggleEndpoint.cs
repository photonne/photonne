using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
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

    private async Task<Results<Ok<FavoriteToggleResponse>, NotFound<ApiError>>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AssetVisibilityService visibility,
        [FromRoute] Guid assetId,
        HttpContext httpContext,
        CancellationToken cancellationToken)
    {
        var asset = await dbContext.Assets
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, cancellationToken);

        if (asset == null || !await visibility.CanReadAsync(httpContext.User, asset, cancellationToken))
            return TypedResults.NotFound(new ApiError($"Asset {assetId} not found", "asset_not_found"));

        asset.IsFavorite = !asset.IsFavorite;
        await dbContext.SaveChangesAsync(cancellationToken);

        return TypedResults.Ok(new FavoriteToggleResponse(asset.IsFavorite));
    }
}

public sealed record FavoriteToggleResponse(bool IsFavorite);
