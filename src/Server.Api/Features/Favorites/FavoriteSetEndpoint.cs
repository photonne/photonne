using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Favorites;

/// <summary>
/// Sets the favorite state to a given value instead of flipping it. With the
/// toggle (<see cref="FavoriteToggleEndpoint"/>, kept for older apps) a client
/// whose copy was stale got the opposite of what the user saw and had to
/// toggle again; repeating a PUT is harmless, so a retry or two devices
/// racing end up where the user asked.
/// </summary>
public class FavoriteSetEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPut("/api/assets/{assetId}/favorite", SetOne)
            .WithName("SetFavorite")
            .WithTags("Assets")
            .WithDescription("Sets the favorite state of an asset and returns it")
            .RequireAuthorization();

        app.MapPut("/api/assets/favorite", SetMany)
            .WithName("SetFavorites")
            .WithTags("Assets")
            .WithDescription("Sets the favorite state of several assets. Returns the ids that now have it; " +
                             "ids that don't exist or the caller can't read are left out")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<FavoriteToggleResponse>, NotFound<ApiError>>> SetOne(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AssetVisibilityService visibility,
        [FromRoute] Guid assetId,
        [FromBody] SetFavoriteRequest request,
        HttpContext httpContext,
        CancellationToken cancellationToken)
    {
        var asset = await dbContext.Assets
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, cancellationToken);

        if (asset == null || !await visibility.CanReadAsync(httpContext.User, asset, cancellationToken))
            return TypedResults.NotFound(new ApiError($"Asset {assetId} not found", "asset_not_found"));

        if (asset.IsFavorite != request.IsFavorite)
        {
            asset.IsFavorite = request.IsFavorite;
            await dbContext.SaveChangesAsync(cancellationToken);
        }

        return TypedResults.Ok(new FavoriteToggleResponse(asset.IsFavorite));
    }

    private static async Task<Results<Ok<SetFavoritesResponse>, BadRequest<ApiError>>> SetMany(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AssetVisibilityService visibility,
        [FromBody] SetFavoritesRequest request,
        HttpContext httpContext,
        CancellationToken cancellationToken)
    {
        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return TypedResults.BadRequest(new ApiError("Debes seleccionar al menos un asset.", "no_assets_selected"));

        var ids = request.AssetIds.Distinct().ToList();
        var assets = await dbContext.Assets
            .Where(a => ids.Contains(a.Id) && a.DeletedAt == null)
            .ToListAsync(cancellationToken);

        // Not all-or-nothing like archive: a selection can mix the caller's
        // photos with ones from a shared album, and the heart of each readable
        // one is what the user asked for. The rest are reported by omission.
        var applied = new List<Guid>(assets.Count);
        foreach (var asset in assets)
        {
            if (!await visibility.CanReadAsync(httpContext.User, asset, cancellationToken)) continue;
            asset.IsFavorite = request.IsFavorite;
            applied.Add(asset.Id);
        }

        await dbContext.SaveChangesAsync(cancellationToken);
        return TypedResults.Ok(new SetFavoritesResponse(applied, request.IsFavorite));
    }
}

public sealed record SetFavoriteRequest(bool IsFavorite);

public sealed record SetFavoritesRequest(List<Guid> AssetIds, bool IsFavorite);

public sealed record SetFavoritesResponse(IReadOnlyList<Guid> AssetIds, bool IsFavorite);
