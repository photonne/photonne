using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

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
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (!Guid.TryParse(userIdClaim?.Value, out var userId))
                return Results.Unauthorized();

            var isAdmin = user.IsInRole("Admin");
            var userRootPath = $"/assets/users/{userId}";

            // Reuse the same cache key as MapAssetsEndpoint to avoid a double DB query
            // when both endpoints are called. Uses a separate typed cache key to avoid
            // type mismatch with the AssetLocation inner class of MapAssetsEndpoint.
            var cacheKey = $"map:points:{userId}";
            if (!cache.TryGetValue(cacheKey, out List<MapPointResponse>? points) || points == null)
            {
                var query = dbContext.Assets
                    .Include(a => a.Exif)
                    .Include(a => a.Thumbnails)
                    .Where(a => a.DeletedAt == null &&
                               a.Exif != null &&
                               a.Exif.Latitude.HasValue &&
                               a.Exif.Longitude.HasValue);

                if (!isAdmin)
                {
                    var allowedFolderIds = await GetAllowedFolderIdsAsync(dbContext, userId, userRootPath, cancellationToken);
                    query = query.Where(a => a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));
                }

                var assets = await query.ToListAsync(cancellationToken);
                points = assets.Select(a => new MapPointResponse
                {
                    Id = a.Id,
                    Latitude = a.Exif!.Latitude!.Value,
                    Longitude = a.Exif.Longitude!.Value,
                    HasThumbnail = a.Thumbnails.Any(),
                    Date = a.FileCreatedAt
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

    private static async Task<HashSet<Guid>> GetAllowedFolderIdsAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string userRootPath,
        CancellationToken ct)
    {
        var allFolders = await dbContext.Folders.ToListAsync(ct);
        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .ToListAsync(ct);

        var foldersWithPermissions = await dbContext.FolderPermissions
            .Select(p => p.FolderId)
            .Distinct()
            .ToListAsync(ct);

        var foldersWithPermissionsSet = foldersWithPermissions.ToHashSet();
        var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();

        foreach (var folder in allFolders)
        {
            if (!foldersWithPermissionsSet.Contains(folder.Id))
            {
                if (folder.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
                {
                    allowedIds.Add(folder.Id);
                }
            }
        }

        // Carpetas de bibliotecas externas accesibles
        var accessibleLibraryIds = await dbContext.ExternalLibraryPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .Select(p => p.ExternalLibraryId)
            .ToHashSetAsync(ct);

        foreach (var folder in allFolders)
        {
            if (folder.ExternalLibraryId.HasValue && accessibleLibraryIds.Contains(folder.ExternalLibraryId.Value))
                allowedIds.Add(folder.Id);
        }

        return allowedIds;
    }
}
