using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;
using Photonne.Server.Api.Features.Timeline;

namespace Photonne.Server.Api.Features.Albums;

public class AlbumsEndpoint : IEndpoint
{
    // Smart-album rules are stored/read as camelCase JSON matching the schema
    // in docs/smart-albums/rule-schema.md.
    private static readonly JsonSerializerOptions SmartRuleJson = new(JsonSerializerDefaults.Web);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/albums")
            .WithTags("Albums")
            .RequireAuthorization();

        group.MapGet("", GetAllAlbums)
            .WithName("GetAllAlbums")
            .WithDescription("Gets all albums accessible by the current user");

        group.MapGet("{albumId:guid}", GetAlbumById)
            .WithName("GetAlbumById")
            .WithDescription("Gets an album by ID");

        group.MapGet("{albumId:guid}/assets", GetAlbumAssets)
            .WithName("GetAlbumAssets")
            .WithDescription("Gets all assets in an album");

        group.MapPost("", CreateAlbum)
            .WithName("CreateAlbum")
            .WithDescription("Creates a new album");

        group.MapPut("{albumId:guid}", UpdateAlbum)
            .WithName("UpdateAlbum")
            .WithDescription("Updates an album");

        group.MapDelete("{albumId:guid}", DeleteAlbum)
            .WithName("DeleteAlbum")
            .WithDescription("Deletes an album");

        group.MapPost("{albumId:guid}/leave", LeaveAlbum)
            .WithName("LeaveAlbum")
            .WithDescription("Removes the current user from a shared album");

        group.MapPost("{albumId:guid}/assets", AddAssetToAlbum)
            .WithName("AddAssetToAlbum")
            .WithDescription("Adds an asset to an album");

        group.MapPost("{albumId:guid}/assets/batch", AddAssetsToAlbumBatch)
            .WithName("AddAssetsToAlbumBatch")
            .WithDescription("Adds multiple assets to an album in a single request");

        group.MapDelete("{albumId:guid}/assets/{assetId:guid}", RemoveAssetFromAlbum)
            .WithName("RemoveAssetFromAlbum")
            .WithDescription("Removes an asset from an album");

        group.MapPut("{albumId:guid}/cover", SetAlbumCover)
            .WithName("SetAlbumCover")
            .WithDescription("Sets the cover image for an album");
    }

    private static async Task<(bool hasAccess, bool canEdit, bool canDelete, bool canManagePermissions)> CheckAlbumPermissionsAsync(
        ApplicationDbContext dbContext,
        Guid albumId,
        Guid userId,
        CancellationToken cancellationToken)
    {
        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return (false, false, false, false);
        }

        // Si es el propietario, tiene todos los permisos
        if (album.OwnerId == userId)
        {
            return (true, true, true, true);
        }

        // Buscar permisos del usuario
        var permission = album.Permissions.FirstOrDefault(p => p.UserId == userId);
        if (permission == null)
        {
            return (false, false, false, false);
        }

        return (permission.CanRead, permission.CanWrite, permission.CanDelete, permission.CanManagePermissions);
    }

    private async Task<IResult> GetAllAlbums(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromServices] SmartAlbumResolver smartResolver,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            var cacheKey = $"albums:{userId}";
            if (cache.TryGetValue(cacheKey, out List<AlbumResponse>? cachedAlbums) && cachedAlbums != null)
                return Results.Ok(cachedAlbums);

            // Obtener álbumes donde el usuario es propietario o tiene permisos
            var albums = await dbContext.Albums
                .Include(a => a.AlbumAssets)
                    .ThenInclude(aa => aa.Asset)
                        .ThenInclude(asset => asset.Thumbnails)
                .Include(a => a.CoverAsset)
                    .ThenInclude(ca => ca!.Thumbnails)
                .Include(a => a.Permissions)
                .Where(a => a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanRead))
                .OrderByDescending(a => a.UpdatedAt)
                .ToListAsync(cancellationToken);

            var albumIds = albums.Select(a => a.Id).ToList();
            var sharedCounts = await dbContext.AlbumPermissions
                .Include(p => p.Album)
                .Where(p => albumIds.Contains(p.AlbumId) && p.CanRead)
                .ToListAsync(cancellationToken);

            var albumSharedCounts = sharedCounts
                .GroupBy(p => p.AlbumId)
                .Select(g => new
                {
                    AlbumId = g.Key,
                    // Contamos todos los que NO son el dueño del álbum
                    Count = g.Count(p => p.UserId != p.Album.OwnerId)
                })
                .ToDictionary(x => x.AlbumId, x => x.Count);

            var now = DateTime.UtcNow;
            var albumsWithActiveLinks = await dbContext.SharedLinks
                .Where(l => l.AlbumId.HasValue &&
                            albumIds.Contains(l.AlbumId.Value) &&
                            (l.ExpiresAt == null || l.ExpiresAt > now) &&
                            (l.MaxViews == null || l.ViewCount < l.MaxViews))
                .Select(l => l.AlbumId!.Value)
                .Distinct()
                .ToHashSetAsync(cancellationToken);

            // Smart albums keep no AlbumAssets rows: resolve membership live so
            // count, cover and preview reflect the rule (owner-anchored, viewer-gated).
            var smartData = new Dictionary<Guid, (int Count, List<Guid> SampleIds)>();
            foreach (var sa in albums.Where(a => a.Kind == AlbumKind.Smart && a.SmartRule != null))
                smartData[sa.Id] = await ResolveSmartSummaryAsync(smartResolver, sa, userId, cancellationToken);

            var response = albums.Select(a => new AlbumResponse
            {
                Id = a.Id,
                Name = a.Name,
                Description = a.Description,
                CreatedAt = a.CreatedAt,
                UpdatedAt = a.UpdatedAt,
                AssetCount = smartData.TryGetValue(a.Id, out var sdCount) ? sdCount.Count : a.AlbumAssets.Count,
                IsOwner = a.OwnerId == userId,
                IsShared = a.Permissions.Any(p => p.CanRead),
                SharedWithCount = albumSharedCounts.TryGetValue(a.Id, out var count) ? count : 0,
                CanRead = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanRead),
                CanWrite = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanWrite),
                CanDelete = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanDelete),
                CanManagePermissions = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanManagePermissions),
                HasActiveShareLink = albumsWithActiveLinks.Contains(a.Id),
                CoverThumbnailUrl = a.CoverAsset?.Thumbnails
                    .FirstOrDefault(t => t.Size == ThumbnailSize.Medium) != null
                    ? $"/api/assets/{a.CoverAssetId}/thumbnail?size=Medium"
                    : smartData.TryGetValue(a.Id, out var sdCover) && sdCover.SampleIds.Count > 0
                        ? $"/api/assets/{sdCover.SampleIds[0]}/thumbnail?size=Medium"
                        : a.AlbumAssets.OrderBy(aa => aa.Order).FirstOrDefault()?.Asset != null
                            ? $"/api/assets/{a.AlbumAssets.OrderBy(aa => aa.Order).First().AssetId}/thumbnail?size=Medium"
                            : null,
                PreviewThumbnailUrls = smartData.TryGetValue(a.Id, out var sdPrev)
                    ? sdPrev.SampleIds.Select(id => $"/api/assets/{id}/thumbnail?size=Small").ToList()
                    : a.AlbumAssets
                        .OrderBy(aa => aa.Order)
                        .Take(4)
                        .Select(aa => $"/api/assets/{aa.AssetId}/thumbnail?size=Small")
                        .ToList()
            }).ToList();

            cache.Set(cacheKey, response, TimeSpan.FromMinutes(5));
            return Results.Ok(response);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] GetAllAlbums: {ex.Message}");
            Console.WriteLine(ex.StackTrace);

            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> GetAlbumById(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SmartAlbumResolver smartResolver,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            var album = await dbContext.Albums
                .Include(a => a.AlbumAssets)
                    .ThenInclude(aa => aa.Asset)
                        .ThenInclude(asset => asset.Thumbnails)
                .Include(a => a.CoverAsset)
                    .ThenInclude(ca => ca!.Thumbnails)
                .Include(a => a.Permissions)
                .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

            if (album == null)
            {
                return Results.NotFound(new { error = $"Album with ID {albumId} not found" });
            }

            // Verificar permisos
            var (hasAccess, _, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
            if (!hasAccess)
            {
                return Results.Forbid();
            }

            var sharedCount = await dbContext.AlbumPermissions
                .CountAsync(p => p.AlbumId == albumId && p.CanRead && p.UserId != album.OwnerId, cancellationToken) + 1;

            // Smart album: resolve membership live for count + cover fallback.
            (int Count, List<Guid> SampleIds)? smartData = null;
            if (album.Kind == AlbumKind.Smart && album.SmartRule != null)
                smartData = await ResolveSmartSummaryAsync(smartResolver, album, userId, cancellationToken);

            string? coverUrl = null;
            if (album.CoverAssetId.HasValue && album.CoverAsset?.Thumbnails.Any(t => t.Size == ThumbnailSize.Medium) == true)
            {
                coverUrl = $"/api/assets/{album.CoverAssetId}/thumbnail?size=Medium";
            }
            else if (smartData is { SampleIds.Count: > 0 } sd)
            {
                coverUrl = $"/api/assets/{sd.SampleIds[0]}/thumbnail?size=Medium";
            }
            else if (album.AlbumAssets.Any())
            {
                var firstAsset = album.AlbumAssets.OrderBy(aa => aa.Order).First().Asset;
                if (firstAsset?.Thumbnails.Any(t => t.Size == ThumbnailSize.Medium) == true)
                {
                    coverUrl = $"/api/assets/{firstAsset.Id}/thumbnail?size=Medium";
                }
            }

            var response = new AlbumResponse
            {
                Id = album.Id,
                Name = album.Name,
                Description = album.Description,
                CreatedAt = album.CreatedAt,
                UpdatedAt = album.UpdatedAt,
                AssetCount = smartData?.Count ?? album.AlbumAssets.Count,
                IsOwner = album.OwnerId == userId,
                IsShared = album.Permissions.Any(p => p.CanRead),
                SharedWithCount = sharedCount,
                CanRead = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanRead),
                CanWrite = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanWrite),
                CanDelete = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanDelete),
                CanManagePermissions = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanManagePermissions),
                CoverThumbnailUrl = coverUrl
            };

            return Results.Ok(response);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] GetAlbumById: {ex.Message}");
            Console.WriteLine(ex.StackTrace);

            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    /// <summary>
    /// Resolves a smart album's live membership to a total count plus up to four
    /// sample asset ids (newest first, thumbnailed) for cover/preview. Returns
    /// zero on an invalid rule so a broken album still lists cleanly.
    /// </summary>
    private static async Task<(int Count, List<Guid> SampleIds)> ResolveSmartSummaryAsync(
        SmartAlbumResolver smartResolver,
        Album album,
        Guid viewerId,
        CancellationToken cancellationToken)
    {
        var rule = JsonSerializer.Deserialize<SmartRuleNode>(album.SmartRule!, SmartRuleJson);
        if (rule == null)
            return (0, new List<Guid>());

        try
        {
            var query = await smartResolver.ResolveAsync(rule, album.OwnerId, viewerId, cancellationToken);
            var count = await query.CountAsync(cancellationToken);
            var sampleIds = await query
                .Where(a => a.Thumbnails.Any())
                .OrderByDescending(a => a.CapturedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .ThenBy(a => a.Id)
                .Take(4)
                .Select(a => a.Id)
                .ToListAsync(cancellationToken);
            return (count, sampleIds);
        }
        catch (SmartRuleException)
        {
            return (0, new List<Guid>());
        }
    }

    private async Task<IResult> GetAlbumAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SmartAlbumResolver smartResolver,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            // Verificar permisos
            var (hasAccess, _, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
            if (!hasAccess)
            {
                return Results.Forbid();
            }

            // Smart album (dynamic): membership is resolved live from the rule,
            // owner-anchored and intersected with the viewer's visibility.
            var smart = await dbContext.Albums
                .AsNoTracking()
                .Where(a => a.Id == albumId && a.Kind == AlbumKind.Smart
                         && a.ResolveMode == SmartResolveMode.Dynamic && a.SmartRule != null)
                .Select(a => new { a.OwnerId, a.SmartRule })
                .FirstOrDefaultAsync(cancellationToken);

            if (smart != null)
            {
                var rule = JsonSerializer.Deserialize<SmartRuleNode>(smart.SmartRule!, SmartRuleJson);
                if (rule == null)
                    return Results.Ok(new List<TimelineResponse>());

                IQueryable<Asset> query;
                try
                {
                    query = await smartResolver.ResolveAsync(rule, smart.OwnerId, userId, cancellationToken);
                }
                catch (SmartRuleException ex)
                {
                    return Results.BadRequest(new { error = ex.Message });
                }

                var smartResponse = await query
                    .OrderByDescending(a => a.CapturedAt)
                    .ThenByDescending(a => a.FileModifiedAt)
                    .ThenBy(a => a.Id)
                    .Select(a => new TimelineResponse
                    {
                        Id = a.Id,
                        FileName = a.FileName,
                        FullPath = a.FullPath,
                        FileSize = a.FileSize,
                        FileCreatedAt = a.CapturedAt,
                        FileModifiedAt = a.FileModifiedAt,
                        Extension = a.Extension,
                        ScannedAt = a.ScannedAt,
                        Type = a.Type == AssetType.Video ? "Video" : "Image",
                        Checksum = a.Checksum,
                        HasExif = a.Exif != null,
                        HasThumbnails = a.Thumbnails.Any(),
                        IsFavorite = a.IsFavorite,
                        SyncStatus = Photonne.Server.Api.Shared.Dtos.AssetSyncStatus.Synced,
                        IsReadOnly = a.ExternalLibraryId.HasValue
                    })
                    .ToListAsync(cancellationToken);

                return Results.Ok(smartResponse);
            }

            var albumAssets = await dbContext.AlbumAssets
                .Include(aa => aa.Asset)
                    .ThenInclude(a => a.Exif)
                .Include(aa => aa.Asset)
                    .ThenInclude(a => a.Thumbnails)
                .Where(aa => aa.AlbumId == albumId && aa.Asset.DeletedAt == null)
                .OrderBy(aa => aa.Order)
                .ThenBy(aa => aa.AddedAt)
                .ToListAsync(cancellationToken);

            var response = albumAssets.Select(aa => new TimelineResponse
            {
                Id = aa.Asset.Id,
                FileName = aa.Asset.FileName,
                FullPath = aa.Asset.FullPath,
                FileSize = aa.Asset.FileSize,
                FileCreatedAt = aa.Asset.FileCreatedAt,
                FileModifiedAt = aa.Asset.FileModifiedAt,
                Extension = aa.Asset.Extension,
                ScannedAt = aa.Asset.ScannedAt,
                Type = aa.Asset.Type.ToString(),
                Checksum = aa.Asset.Checksum,
                HasExif = aa.Asset.Exif != null,
                HasThumbnails = aa.Asset.Thumbnails.Any(),
                IsFavorite = aa.Asset.IsFavorite,
                SyncStatus = Photonne.Server.Api.Shared.Dtos.AssetSyncStatus.Synced,
                DeletedAt = aa.Asset.DeletedAt,
                IsReadOnly = aa.Asset.ExternalLibraryId.HasValue
            }).ToList();

            return Results.Ok(response);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] GetAlbumAssets: {ex.Message}");
            Console.WriteLine(ex.StackTrace);

            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> CreateAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromBody] CreateAlbumRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            if (request == null || string.IsNullOrWhiteSpace(request.Name))
            {
                return Results.BadRequest(new { error = "Album name is required" });
            }

            var album = new Album
            {
                Name = request.Name.Trim(),
                Description = request.Description?.Trim(),
                OwnerId = userId,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };

            // Smart album: validate the rule compiles (so a broken tree is a 400,
            // not a runtime failure on first open) and store it verbatim — the
            // original folder ids are kept and subtree-expanded fresh on each read.
            if (request.SmartRule != null)
            {
                try
                {
                    SmartRuleCompiler.Compile(request.SmartRule, dbContext, userId);
                }
                catch (SmartRuleException ex)
                {
                    return Results.BadRequest(new { error = ex.Message });
                }

                album.Kind = AlbumKind.Smart;
                album.SmartRule = JsonSerializer.Serialize(request.SmartRule, SmartRuleJson);
            }

            dbContext.Albums.Add(album);
            await dbContext.SaveChangesAsync(cancellationToken);
            cache.Remove($"albums:{userId}");

            var response = new AlbumResponse
            {
                Id = album.Id,
                Name = album.Name,
                Description = album.Description,
                CreatedAt = album.CreatedAt,
                UpdatedAt = album.UpdatedAt,
                AssetCount = 0,
                IsOwner = true,
                CanRead = true,
                CanWrite = true,
                CanDelete = true,
                CanManagePermissions = true,
                Kind = album.Kind.ToString()
            };

            return Results.Created($"/api/albums/{album.Id}", response);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] CreateAlbum: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
            
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> UpdateAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromBody] UpdateAlbumRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            // Verificar permisos de edición
            var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
            if (!hasAccess || !canEdit)
            {
                return Results.Forbid();
            }

            var album = await dbContext.Albums
                .Include(a => a.Permissions)
                .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

            if (album == null)
            {
                return Results.NotFound(new { error = $"Album with ID {albumId} not found" });
            }

            if (request == null || string.IsNullOrWhiteSpace(request.Name))
            {
                return Results.BadRequest(new { error = "Album name is required" });
            }

            album.Name = request.Name.Trim();
            album.Description = request.Description?.Trim();
            album.UpdatedAt = DateTime.UtcNow;

            await dbContext.SaveChangesAsync(cancellationToken);
            cache.Remove($"albums:{userId}");

            var response = new AlbumResponse
            {
                Id = album.Id,
                Name = album.Name,
                Description = album.Description,
                CreatedAt = album.CreatedAt,
                UpdatedAt = album.UpdatedAt,
                AssetCount = await dbContext.AlbumAssets.CountAsync(aa => aa.AlbumId == albumId, cancellationToken),
                IsOwner = album.OwnerId == userId,
                CanRead = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanRead),
                CanWrite = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanWrite),
                CanDelete = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanDelete),
                CanManagePermissions = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanManagePermissions)
            };

            return Results.Ok(response);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] UpdateAlbum: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
            
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> DeleteAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            // Verificar permisos de eliminación
            var (hasAccess, _, canDelete, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
            if (!hasAccess || !canDelete)
            {
                return Results.Forbid();
            }

            var album = await dbContext.Albums
                .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

            if (album == null)
            {
                return Results.NotFound(new { error = $"Album with ID {albumId} not found" });
            }

            dbContext.Albums.Remove(album);
            await dbContext.SaveChangesAsync(cancellationToken);
            cache.Remove($"albums:{userId}");

            return Results.NoContent();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] DeleteAlbum: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
            
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> LeaveAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            var album = await dbContext.Albums
                .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

            if (album == null)
            {
                return Results.NotFound(new { error = $"Album with ID {albumId} not found" });
            }

            if (album.OwnerId == userId)
            {
                return Results.BadRequest(new { error = "Owner cannot leave their own album" });
            }

            var permission = await dbContext.AlbumPermissions
                .FirstOrDefaultAsync(p => p.AlbumId == albumId && p.UserId == userId, cancellationToken);

            if (permission == null)
            {
                return Results.NotFound(new { error = "Permission not found for current user" });
            }

            dbContext.AlbumPermissions.Remove(permission);
            await dbContext.SaveChangesAsync(cancellationToken);
            cache.Remove($"albums:{userId}");

            return Results.NoContent();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] LeaveAlbum: {ex.Message}");
            Console.WriteLine(ex.StackTrace);

            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> AddAssetToAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromBody] AddAssetRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            // Verificar permisos de edición
            var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
            if (!hasAccess || !canEdit)
            {
                return Results.Forbid();
            }

            if (request == null)
            {
                return Results.BadRequest(new { error = "Request body is required" });
            }

            var album = await dbContext.Albums
                .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

            if (album == null)
            {
                return Results.NotFound(new { error = $"Album with ID {albumId} not found" });
            }

            var asset = await dbContext.Assets
                .FirstOrDefaultAsync(a => a.Id == request.AssetId && a.DeletedAt == null, cancellationToken);

            if (asset == null)
            {
                return Results.NotFound(new { error = $"Asset with ID {request.AssetId} not found" });
            }

            // Check if asset is already in album
            var existing = await dbContext.AlbumAssets
                .AnyAsync(aa => aa.AlbumId == albumId && aa.AssetId == request.AssetId, cancellationToken);

            if (existing)
            {
                return Results.BadRequest(new { error = "Asset is already in this album" });
            }

            // Get the next order value
            var maxOrder = 0;
            if (await dbContext.AlbumAssets.AnyAsync(aa => aa.AlbumId == albumId, cancellationToken))
            {
                maxOrder = await dbContext.AlbumAssets
                    .Where(aa => aa.AlbumId == albumId)
                    .MaxAsync(aa => aa.Order, cancellationToken);
            }

            var albumAsset = new AlbumAsset
            {
                AlbumId = albumId,
                AssetId = request.AssetId,
                Order = maxOrder + 1,
                AddedAt = DateTime.UtcNow
            };

            dbContext.AlbumAssets.Add(albumAsset);

            // If album has no cover, set this as cover
            if (album.CoverAssetId == null)
            {
                album.CoverAssetId = request.AssetId;
            }

            album.UpdatedAt = DateTime.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
            cache.Remove($"albums:{userId}");

            return Results.Ok(new { message = "Asset added to album successfully" });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] AddAssetToAlbum: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
            
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> AddAssetsToAlbumBatch(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromBody] AddAssetsBatchRequest request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
            return Results.Forbid();

        var album = await dbContext.Albums.FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);
        if (album == null) return Results.NotFound();

        if (request.AssetIds.Count == 0)
            return Results.BadRequest(new { error = "AssetIds must not be empty" });

        // Load existing entries to skip duplicates
        var existingIds = await dbContext.AlbumAssets
            .Where(aa => aa.AlbumId == albumId)
            .Select(aa => aa.AssetId)
            .ToHashSetAsync(cancellationToken);

        var toAdd = request.AssetIds.Distinct().Where(id => !existingIds.Contains(id)).ToList();

        // Validate all asset IDs exist
        var validIds = await dbContext.Assets
            .Where(a => toAdd.Contains(a.Id) && a.DeletedAt == null)
            .Select(a => a.Id)
            .ToListAsync(cancellationToken);

        var order = await dbContext.AlbumAssets
            .Where(aa => aa.AlbumId == albumId)
            .CountAsync(cancellationToken);

        foreach (var assetId in validIds)
        {
            dbContext.AlbumAssets.Add(new AlbumAsset { AlbumId = albumId, AssetId = assetId, Order = ++order });
        }

        if (album.CoverAssetId == null && validIds.Count > 0)
            album.CoverAssetId = validIds[0];

        album.UpdatedAt = DateTime.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return Results.Ok(new { added = validIds.Count, skipped = request.AssetIds.Count - validIds.Count });
    }

    private async Task<IResult> RemoveAssetFromAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromRoute] Guid assetId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            // Verificar permisos de edición
            var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
            if (!hasAccess || !canEdit)
            {
                return Results.Forbid();
            }

            var albumAsset = await dbContext.AlbumAssets
                .FirstOrDefaultAsync(aa => aa.AlbumId == albumId && aa.AssetId == assetId, cancellationToken);

            if (albumAsset == null)
            {
                return Results.NotFound(new { error = "Asset not found in this album" });
            }

            var album = await dbContext.Albums
                .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

            if (album != null)
            {
                // If this was the cover asset, clear it
                if (album.CoverAssetId == assetId)
                {
                    album.CoverAssetId = null;
                }
                album.UpdatedAt = DateTime.UtcNow;
            }

            dbContext.AlbumAssets.Remove(albumAsset);
            await dbContext.SaveChangesAsync(cancellationToken);
            cache.Remove($"albums:{userId}");

            return Results.NoContent();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] RemoveAssetFromAlbum: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
            
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private async Task<IResult> SetAlbumCover(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromServices] SmartAlbumResolver smartResolver,
        [FromRoute] Guid albumId,
        [FromBody] SetCoverRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            // Verificar permisos de edición
            var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
            if (!hasAccess || !canEdit)
            {
                return Results.Forbid();
            }

            if (request == null)
            {
                return Results.BadRequest(new { error = "Request body is required" });
            }

            var album = await dbContext.Albums
                .Include(a => a.AlbumAssets)
                    .ThenInclude(aa => aa.Asset)
                        .ThenInclude(asset => asset.Thumbnails)
                .Include(a => a.Permissions)
                .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

            if (album == null)
            {
                return Results.NotFound(new { error = $"Album with ID {albumId} not found" });
            }

            // Membership: manual albums check AlbumAssets; smart albums have none,
            // so the cover must be an asset the live rule actually resolves.
            var isSmart = album.Kind == AlbumKind.Smart && album.SmartRule != null;
            if (isSmart)
            {
                var rule = JsonSerializer.Deserialize<SmartRuleNode>(album.SmartRule!, SmartRuleJson);
                var inAlbum = false;
                if (rule != null)
                {
                    try
                    {
                        var q = await smartResolver.ResolveAsync(rule, album.OwnerId, userId, cancellationToken);
                        inAlbum = await q.AnyAsync(a => a.Id == request.AssetId, cancellationToken);
                    }
                    catch (SmartRuleException) { inAlbum = false; }
                }
                if (!inAlbum)
                    return Results.BadRequest(new { error = "Asset must be in the album to set it as cover" });
            }
            else if (album.AlbumAssets.All(aa => aa.AssetId != request.AssetId))
            {
                return Results.BadRequest(new { error = "Asset must be in the album to set it as cover" });
            }

            album.CoverAssetId = request.AssetId;
            album.UpdatedAt = DateTime.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
            cache.Remove($"albums:{userId}");

            var now = DateTime.UtcNow;
            var hasActiveShareLink = await dbContext.SharedLinks
                .AnyAsync(l => l.AlbumId == albumId &&
                               (l.ExpiresAt == null || l.ExpiresAt > now) &&
                               (l.MaxViews == null || l.ViewCount < l.MaxViews),
                    cancellationToken);

            // Works for both kinds: the cover asset may not be in AlbumAssets (smart).
            var coverHasMediumThumbnail = await dbContext.Assets
                .Where(a => a.Id == request.AssetId)
                .SelectMany(a => a.Thumbnails)
                .AnyAsync(t => t.Size == ThumbnailSize.Medium, cancellationToken);

            var smartData = isSmart
                ? await ResolveSmartSummaryAsync(smartResolver, album, userId, cancellationToken)
                : ((int Count, List<Guid> SampleIds)?)null;

            var response = new AlbumResponse
            {
                Id = album.Id,
                Name = album.Name,
                Description = album.Description,
                CreatedAt = album.CreatedAt,
                UpdatedAt = album.UpdatedAt,
                AssetCount = smartData?.Count ?? album.AlbumAssets.Count,
                IsOwner = album.OwnerId == userId,
                IsShared = album.Permissions.Any(p => p.CanRead),
                SharedWithCount = album.Permissions.Count(p => p.CanRead && p.UserId != album.OwnerId),
                CanRead = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanRead),
                CanWrite = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanWrite),
                CanDelete = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanDelete),
                CanManagePermissions = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanManagePermissions),
                HasActiveShareLink = hasActiveShareLink,
                CoverThumbnailUrl = coverHasMediumThumbnail
                    ? $"/api/assets/{request.AssetId}/thumbnail?size=Medium"
                    : null,
                PreviewThumbnailUrls = smartData != null
                    ? smartData.Value.SampleIds.Select(id => $"/api/assets/{id}/thumbnail?size=Small").ToList()
                    : album.AlbumAssets
                        .OrderBy(aa => aa.Order)
                        .Take(4)
                        .Select(aa => $"/api/assets/{aa.AssetId}/thumbnail?size=Small")
                        .ToList()
            };

            return Results.Ok(response);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] SetAlbumCover: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
            
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }
}

public class AlbumResponse
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public int AssetCount { get; set; }
    public string? CoverThumbnailUrl { get; set; }
    public List<string> PreviewThumbnailUrls { get; set; } = new();
    public bool IsOwner { get; set; }
    public bool IsShared { get; set; }
    public int SharedWithCount { get; set; }
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    public bool HasActiveShareLink { get; set; }
    // "Manual" or "Smart" (docs/smart-albums/).
    public string Kind { get; set; } = nameof(AlbumKind.Manual);
}

public class CreateAlbumRequest
{
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    // When present, the album is a smart album: membership is derived from this
    // rule tree (docs/smart-albums/rule-schema.md) instead of a manual list.
    public Shared.Services.SmartAlbums.SmartRuleNode? SmartRule { get; set; }
}

public class UpdateAlbumRequest
{
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
}

public class AddAssetRequest
{
    public Guid AssetId { get; set; }
}

public class AddAssetsBatchRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class SetCoverRequest
{
    public Guid AssetId { get; set; }
}
