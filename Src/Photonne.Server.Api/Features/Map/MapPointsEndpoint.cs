using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Map;

public class MapPointsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/map/points", Handle)
            .WithName("GetMapPoints")
            .WithTags("Assets")
            .WithDescription("Gets all assets with GPS coordinates as raw points for client-side clustering");
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (!Guid.TryParse(userIdClaim?.Value, out var userId))
                return Results.Unauthorized();
            var username = user.GetUsername();
            if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

            var userRootPath = $"/assets/users/{username}";

            // Reuse the same cache key as MapAssetsEndpoint to avoid a double DB query
            // when both endpoints are called. Uses a separate typed cache key to avoid
            // type mismatch with the AssetLocation inner class of MapAssetsEndpoint.
            var cacheKey = $"map:points:{userId}";
            if (!cache.TryGetValue(cacheKey, out List<MapPointResponse>? points) || points == null)
            {
                var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
                    dbContext, userId, userRootPath, cancellationToken);
                var assets = await dbContext.Assets
                    .Include(a => a.Exif)
                    .Include(a => a.Thumbnails)
                    .Where(a => a.DeletedAt == null &&
                               !a.IsFileMissing &&
                               a.Exif != null &&
                               a.Exif.Latitude.HasValue &&
                               a.Exif.Longitude.HasValue &&
                               // Excluir (0,0) — GPS vacío/corrupto en EXIF
                               (a.Exif.Latitude.Value > 0.0001 || a.Exif.Latitude.Value < -0.0001 ||
                                a.Exif.Longitude.Value > 0.0001 || a.Exif.Longitude.Value < -0.0001) &&
                               a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value))
                    .ToListAsync(cancellationToken);
                points = assets.Select(a => new MapPointResponse
                {
                    Id = a.Id,
                    Latitude = a.Exif!.Latitude!.Value,
                    Longitude = a.Exif.Longitude!.Value,
                    HasThumbnail = a.Thumbnails.Any(),
                    Date = a.CapturedAt
                }).ToList();

                cache.Set(cacheKey, points, TimeSpan.FromMinutes(5));
            }

            return Results.Ok(points);
        }
        catch (Exception ex)
        {
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError);
        }
    }

}
