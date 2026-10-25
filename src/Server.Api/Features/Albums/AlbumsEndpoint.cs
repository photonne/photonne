using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Extensions;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;
using Photonne.Server.Api.Features.Pins;
using Photonne.Server.Api.Features.Timeline;

namespace Photonne.Server.Api.Features.Albums;

public class AlbumsEndpoint : IEndpoint
{
    // Smart-album rules are stored/read as camelCase JSON matching the schema
    // in docs/smart-albums/rule-schema.md.
    private static readonly JsonSerializerOptions SmartRuleJson = new(JsonSerializerDefaults.Web);

    // A smart album's members come from its rule; edit the rule instead.
    private static readonly ApiError SmartAlbumIsRuleManaged =
        new("A smart album's photos come from its rule", "smart_album_rule_managed");

    // How long a photo taken out of an album can still be listed and put back.
    public static readonly TimeSpan RemovalRetention = TimeSpan.FromDays(30);

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

        group.MapGet("{albumId:guid}/rule", GetAlbumRule)
            .WithName("GetAlbumRule")
            .WithDescription("Gets a smart album's rule plus display names for the ids it references");

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

        group.MapPost("{albumId:guid}/assets/remove", RemoveAssetsFromAlbumBatch)
            .WithName("RemoveAssetsFromAlbumBatch")
            .WithDescription("Removes multiple assets from an album in a single request; they can be restored for a while");

        group.MapGet("{albumId:guid}/assets/removed", GetRemovedAlbumAssets)
            .WithName("GetRemovedAlbumAssets")
            .WithDescription("Lists the assets recently removed from an album, newest removal first, with who removed them");

        group.MapPost("{albumId:guid}/assets/restore", RestoreAlbumAssets)
            .WithName("RestoreAlbumAssets")
            .WithDescription("Puts recently removed assets back in an album, in their original place");

        group.MapPut("{albumId:guid}/cover", SetAlbumCover)
            .WithName("SetAlbumCover")
            .WithDescription("Sets the cover image for an album");

        group.MapPut("{albumId:guid}/pin", PinAlbum)
            .WithName("PinAlbum")
            .WithDescription("Pins an album for the current user only (idempotent)");

        group.MapDelete("{albumId:guid}/pin", UnpinAlbum)
            .WithName("UnpinAlbum")
            .WithDescription("Unpins an album for the current user only (idempotent)");
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

    /// <summary>The viewer's own pin on an album, or null. Pins are per user.</summary>
    private static Task<AlbumPin?> GetPinAsync(
        ApplicationDbContext dbContext, Guid albumId, Guid userId, CancellationToken cancellationToken) =>
        dbContext.AlbumPins.AsNoTracking()
            .FirstOrDefaultAsync(p => p.UserId == userId && p.AlbumId == albumId, cancellationToken);

    private async Task<Results<Ok<List<AlbumResponse>>, UnauthorizedHttpResult>> GetAllAlbums(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        var cacheKey = $"albums:{userId}";
        if (cache.TryGetValue(cacheKey, out List<AlbumResponse>? cachedAlbums) && cachedAlbums != null)
            return TypedResults.Ok(cachedAlbums);

        // Obtener álbumes donde el usuario es propietario o tiene permisos
        var albums = await dbContext.Albums
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

        // Pins are personal: only the viewer's own rows. The list order is
        // left alone — clients sort and group pinned albums themselves.
        var pins = await dbContext.AlbumPins.AsNoTracking()
            .Where(p => p.UserId == userId && albumIds.Contains(p.AlbumId))
            .ToDictionaryAsync(p => p.AlbumId, cancellationToken);

        var contents = await LoadContentsAsync(dbContext, albums, cancellationToken);

        var response = albums.Select(a => new AlbumResponse
        {
            Id = a.Id,
            Name = a.Name,
            Description = a.Description,
            CreatedAt = a.CreatedAt,
            UpdatedAt = a.UpdatedAt,
            AssetCount = contents[a.Id].Count,
            IsOwner = a.OwnerId == userId,
            IsShared = a.Permissions.Any(p => p.CanRead),
            SharedWithCount = albumSharedCounts.TryGetValue(a.Id, out var count) ? count : 0,
            CanRead = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanRead),
            CanWrite = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanWrite),
            CanDelete = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanDelete),
            CanManagePermissions = a.OwnerId == userId || a.Permissions.Any(p => p.UserId == userId && p.CanManagePermissions),
            HasActiveShareLink = albumsWithActiveLinks.Contains(a.Id),
            IsPinned = pins.ContainsKey(a.Id),
            PinnedAt = pins.GetValueOrDefault(a.Id)?.PinnedAt,
            PinPosition = pins.GetValueOrDefault(a.Id)?.Position,
            Kind = a.Kind.ToString(),
            CoverThumbnailUrl = a.CoverAsset?.Thumbnails
                .FirstOrDefault(t => t.Size == ThumbnailSize.Medium) != null
                ? $"/api/assets/{a.CoverAssetId}/thumbnail?size=Medium"
                : contents[a.Id].PreviewIds.Count > 0
                    ? $"/api/assets/{contents[a.Id].PreviewIds[0]}/thumbnail?size=Medium"
                    : null,
            PreviewThumbnailUrls = PreviewUrls(contents[a.Id])
        }).ToList();

        cache.Set(cacheKey, response, TimeSpan.FromMinutes(5));
        return TypedResults.Ok(response);
    }

    private async Task<Results<Ok<AlbumResponse>, UnauthorizedHttpResult, NotFound<ApiError>, ForbidHttpResult>> GetAlbumById(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        var album = await dbContext.Albums
            .Include(a => a.CoverAsset)
                .ThenInclude(ca => ca!.Thumbnails)
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        }

        // Verificar permisos
        var (hasAccess, _, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess)
        {
            return TypedResults.Forbid();
        }

        var sharedCount = await dbContext.AlbumPermissions
            .CountAsync(p => p.AlbumId == albumId && p.CanRead && p.UserId != album.OwnerId, cancellationToken) + 1;

        var contents = (await LoadContentsAsync(dbContext, new[] { album }, cancellationToken))[album.Id];

        var pin = await GetPinAsync(dbContext, albumId, userId, cancellationToken);

        string? coverUrl = null;
        if (album.CoverAssetId.HasValue && album.CoverAsset?.Thumbnails.Any(t => t.Size == ThumbnailSize.Medium) == true)
        {
            coverUrl = $"/api/assets/{album.CoverAssetId}/thumbnail?size=Medium";
        }
        else if (contents.PreviewIds.Count > 0)
        {
            var firstId = contents.PreviewIds[0];
            var firstHasMedium = await dbContext.Assets
                .Where(a => a.Id == firstId)
                .SelectMany(a => a.Thumbnails)
                .AnyAsync(t => t.Size == ThumbnailSize.Medium, cancellationToken);
            if (firstHasMedium)
            {
                coverUrl = $"/api/assets/{firstId}/thumbnail?size=Medium";
            }
        }

        var response = new AlbumResponse
        {
            Id = album.Id,
            Name = album.Name,
            Description = album.Description,
            CreatedAt = album.CreatedAt,
            UpdatedAt = album.UpdatedAt,
            AssetCount = contents.Count,
            IsOwner = album.OwnerId == userId,
            IsShared = album.Permissions.Any(p => p.CanRead),
            SharedWithCount = sharedCount,
            CanRead = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanRead),
            CanWrite = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanWrite),
            CanDelete = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanDelete),
            CanManagePermissions = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanManagePermissions),
            CoverThumbnailUrl = coverUrl,
            IsPinned = pin != null,
            PinnedAt = pin?.PinnedAt,
            PinPosition = pin?.Position,
            Kind = album.Kind.ToString()
        };

        return TypedResults.Ok(response);
    }

    /// <summary>
    /// Returns a smart album's stored rule for the edit screen, plus names for
    /// the person and folder ids it references so the editor can show chips
    /// without paging the whole people/folder catalogs. Owner-only, like the
    /// rule edit itself (the ids are the owner's own people).
    /// </summary>
    private async Task<Results<Ok<SmartAlbumRuleResponse>, UnauthorizedHttpResult, NotFound<ApiError>, ForbidHttpResult>> GetAlbumRule(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var album = await dbContext.Albums.AsNoTracking()
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);
        if (album == null || album.Kind != AlbumKind.Smart || album.SmartRule == null)
            return TypedResults.NotFound(new ApiError($"Smart album with ID {albumId} not found", "album_not_found"));
        if (album.OwnerId != userId)
            return TypedResults.Forbid();

        var rule = JsonSerializer.Deserialize<SmartRuleNode>(album.SmartRule, SmartRuleJson);
        if (rule == null)
            return TypedResults.NotFound(new ApiError("Album rule is empty", "album_rule_empty"));

        var personIds = new HashSet<Guid>();
        var folderIds = new HashSet<Guid>();
        CollectRuleIds(rule, personIds, folderIds);

        var people = await dbContext.People.AsNoTracking()
            .Where(p => personIds.Contains(p.Id) && p.OwnerId == album.OwnerId)
            .Select(p => new SmartRulePersonRef { Id = p.Id, Name = p.Name, CoverFaceId = p.CoverFaceId })
            .ToListAsync(cancellationToken);

        var folders = (await dbContext.Folders.AsNoTracking()
                .Where(f => folderIds.Contains(f.Id))
                .Select(f => new { f.Id, f.Name, f.Path })
                .ToListAsync(cancellationToken))
            .Select(f => new SmartRuleFolderRef
            {
                Id = f.Id,
                Name = f.Name,
                Path = f.Path,
                IsShared = f.Path.StartsWith("/assets/shared", StringComparison.OrdinalIgnoreCase)
            })
            .ToList();

        return TypedResults.Ok(new SmartAlbumRuleResponse { Rule = rule, People = people, Folders = folders });
    }

    private static void CollectRuleIds(SmartRuleNode node, HashSet<Guid> personIds, HashSet<Guid> folderIds)
    {
        if (node.PersonIds != null) personIds.UnionWith(node.PersonIds);
        if (node.FolderIds != null) folderIds.UnionWith(node.FolderIds);
        if (node.Condition != null) CollectRuleIds(node.Condition, personIds, folderIds);
        if (node.Conditions != null)
            foreach (var child in node.Conditions)
                CollectRuleIds(child, personIds, folderIds);
    }

    private sealed record AlbumContents(int Count, List<Guid> PreviewIds);

    /// <summary>
    /// Member count plus up to four preview ids per album, projected in SQL
    /// rather than loading the rows — a smart album can hold thousands. Manual
    /// albums preview in their own order; smart ones newest first, skipping
    /// photos without thumbnails yet, and count what opening them would show.
    /// </summary>
    private static async Task<Dictionary<Guid, AlbumContents>> LoadContentsAsync(
        ApplicationDbContext dbContext, IEnumerable<Album> albums, CancellationToken cancellationToken)
    {
        var list = albums.ToList();
        var manualIds = list.Where(a => a.Kind != AlbumKind.Smart).Select(a => a.Id).ToList();
        var smartIds = list.Where(a => a.Kind == AlbumKind.Smart).Select(a => a.Id).ToList();

        var manual = manualIds.Count == 0 ? [] : await dbContext.Albums.AsNoTracking()
            .Where(a => manualIds.Contains(a.Id))
            .Select(a => new
            {
                a.Id,
                Count = a.AlbumAssets.Count(),
                PreviewIds = a.AlbumAssets.OrderBy(aa => aa.Order).Take(4).Select(aa => aa.AssetId).ToList()
            })
            .ToListAsync(cancellationToken);

        var smart = smartIds.Count == 0 ? [] : await dbContext.Albums.AsNoTracking()
            .Where(a => smartIds.Contains(a.Id))
            .Select(a => new
            {
                a.Id,
                Count = a.AlbumAssets.Count(aa =>
                    aa.Asset.DeletedAt == null && !aa.Asset.IsArchived && !aa.Asset.IsFileMissing),
                PreviewIds = a.AlbumAssets
                    .Where(aa => aa.Asset.DeletedAt == null && !aa.Asset.IsArchived && !aa.Asset.IsFileMissing
                              && aa.Asset.Thumbnails.Any())
                    .OrderByDescending(aa => aa.Asset.CapturedAt)
                    .ThenByDescending(aa => aa.Asset.FileModifiedAt)
                    .ThenBy(aa => aa.AssetId)
                    .Take(4)
                    .Select(aa => aa.AssetId)
                    .ToList()
            })
            .ToListAsync(cancellationToken);

        return manual.Select(m => (m.Id, new AlbumContents(m.Count, m.PreviewIds)))
            .Concat(smart.Select(m => (m.Id, new AlbumContents(m.Count, m.PreviewIds))))
            .ToDictionary(x => x.Id, x => x.Item2);
    }

    private static List<string> PreviewUrls(AlbumContents contents) =>
        contents.PreviewIds.Select(id => $"/api/assets/{id}/thumbnail?size=Small").ToList();

    private async Task<Results<Ok<List<TimelineResponse>>, UnauthorizedHttpResult, ForbidHttpResult>> GetAlbumAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SmartAlbumMaterializer materializer,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        // Verificar permisos
        var (hasAccess, _, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess)
        {
            return TypedResults.Forbid();
        }

        // Smart album: refresh its rows from the rule on open, so what was
        // indexed or tagged since shows up, then read them like any album's —
        // newest first, as the rule has no order of its own.
        var isSmart = await dbContext.Albums
            .AnyAsync(a => a.Id == albumId && a.Kind == AlbumKind.Smart, cancellationToken);

        if (isSmart)
        {
            await materializer.MaterializeAsync(albumId, cancellationToken);

            var query = dbContext.AlbumAssets
                .AsNoTracking()
                .Where(aa => aa.AlbumId == albumId)
                .Select(aa => aa.Asset)
                .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing);

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
                    IsReadOnly = a.ExternalLibraryId.HasValue,
                    Width = a.Exif != null ? a.Exif.Width : null,
                    Height = a.Exif != null ? a.Exif.Height : null,
                    DominantColor = a.Thumbnails
                        .Where(t => t.Size == ThumbnailSize.Small)
                        .Select(t => t.DominantColor)
                        .FirstOrDefault(),
                    AspectRatio = a.Thumbnails
                        .Where(t => t.Size == ThumbnailSize.Small && t.Height > 0)
                        .Select(t => (double?)t.Width / t.Height)
                        .FirstOrDefault(),
                    ThumbnailsGeneratedAt = a.Thumbnails
                        .Where(t => t.Size == ThumbnailSize.Small)
                        .Select(t => (DateTime?)t.CreatedAt)
                        .FirstOrDefault()
                })
                .ToListAsync(cancellationToken);

            return TypedResults.Ok(smartResponse);
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

        var response = albumAssets.Select(aa => ToAlbumItem(aa.Asset)).ToList();

        return TypedResults.Ok(response);
    }

    /// <summary>A manual album's photo as the grids read it; Exif and Thumbnails must be loaded.</summary>
    private static TimelineResponse ToAlbumItem(Asset asset) => new TimelineResponse
    {
        Id = asset.Id,
        FileName = asset.FileName,
        FullPath = asset.FullPath,
        FileSize = asset.FileSize,
        FileCreatedAt = asset.FileCreatedAt,
        FileModifiedAt = asset.FileModifiedAt,
        Extension = asset.Extension,
        ScannedAt = asset.ScannedAt,
        Type = asset.Type.ToString(),
        Checksum = asset.Checksum,
        HasExif = asset.Exif != null,
        HasThumbnails = asset.Thumbnails.Any(),
        IsFavorite = asset.IsFavorite,
        SyncStatus = Photonne.Server.Api.Shared.Dtos.AssetSyncStatus.Synced,
        DeletedAt = asset.DeletedAt,
        IsReadOnly = asset.ExternalLibraryId.HasValue
    }.WithThumbnailShape(asset);

    private async Task<Results<Created<AlbumResponse>, UnauthorizedHttpResult, BadRequest<ApiError>>> CreateAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromServices] SmartAlbumMaterializer materializer,
        [FromBody] CreateAlbumRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        if (request == null || string.IsNullOrWhiteSpace(request.Name))
        {
            return TypedResults.BadRequest(new ApiError("Album name is required", "album_name_required"));
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
        // original folder ids are kept and subtree-expanded fresh on each pass.
        if (request.SmartRule != null)
        {
            try
            {
                SmartRuleCompiler.Compile(request.SmartRule, dbContext, userId);
            }
            catch (SmartRuleException ex)
            {
                return TypedResults.BadRequest(new ApiError(ex.Message, "invalid_rule"));
            }

            album.Kind = AlbumKind.Smart;
            album.ResolveMode = SmartResolveMode.Materialized;
            album.SmartRule = JsonSerializer.Serialize(request.SmartRule, SmartRuleJson);
        }

        dbContext.Albums.Add(album);
        await dbContext.SaveChangesAsync(cancellationToken);
        if (album.Kind == AlbumKind.Smart)
            await materializer.MaterializeAsync(album.Id, cancellationToken);
        cache.Remove($"albums:{userId}");

        var contents = (await LoadContentsAsync(dbContext, new[] { album }, cancellationToken))[album.Id];

        var response = new AlbumResponse
        {
            Id = album.Id,
            Name = album.Name,
            Description = album.Description,
            CreatedAt = album.CreatedAt,
            UpdatedAt = album.UpdatedAt,
            AssetCount = contents.Count,
            IsOwner = true,
            CanRead = true,
            CanWrite = true,
            CanDelete = true,
            CanManagePermissions = true,
            Kind = album.Kind.ToString()
        };

        return TypedResults.Created($"/api/albums/{album.Id}", response);
    }

    private async Task<Results<Ok<AlbumResponse>, UnauthorizedHttpResult, ForbidHttpResult, NotFound<ApiError>, BadRequest<ApiError>>> UpdateAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromServices] SmartAlbumMaterializer materializer,
        [FromRoute] Guid albumId,
        [FromBody] UpdateAlbumRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        // Verificar permisos de edición
        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
        {
            return TypedResults.Forbid();
        }

        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        }

        if (request == null || string.IsNullOrWhiteSpace(request.Name))
        {
            return TypedResults.BadRequest(new ApiError("Album name is required", "album_name_required"));
        }

        // Rule edit (smart albums only). The rule is owner-anchored — person ids
        // are the owner's own clusters — so only the owner may replace it; a
        // collaborator with CanWrite can still rename. Null = keep the rule.
        if (request.SmartRule != null)
        {
            if (album.Kind != AlbumKind.Smart)
                return TypedResults.BadRequest(new ApiError("Only smart albums have a rule", "not_smart_album"));
            if (album.OwnerId != userId)
                return TypedResults.Forbid();

            try
            {
                SmartRuleCompiler.Compile(request.SmartRule, dbContext, album.OwnerId);
            }
            catch (SmartRuleException ex)
            {
                return TypedResults.BadRequest(new ApiError(ex.Message, "invalid_rule"));
            }

            album.SmartRule = JsonSerializer.Serialize(request.SmartRule, SmartRuleJson);
        }

        album.Name = request.Name.Trim();
        album.Description = request.Description?.Trim();
        album.UpdatedAt = DateTime.UtcNow;

        await dbContext.SaveChangesAsync(cancellationToken);
        if (request.SmartRule != null)
            await materializer.MaterializeAsync(album.Id, cancellationToken);
        cache.Remove($"albums:{userId}");

        var contents = (await LoadContentsAsync(dbContext, new[] { album }, cancellationToken))[album.Id];

        var response = new AlbumResponse
        {
            Id = album.Id,
            Name = album.Name,
            Description = album.Description,
            CreatedAt = album.CreatedAt,
            UpdatedAt = album.UpdatedAt,
            AssetCount = contents.Count,
            Kind = album.Kind.ToString(),
            IsOwner = album.OwnerId == userId,
            CanRead = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanRead),
            CanWrite = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanWrite),
            CanDelete = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanDelete),
            CanManagePermissions = album.OwnerId == userId || album.Permissions.Any(p => p.UserId == userId && p.CanManagePermissions)
        };
        var pin = await GetPinAsync(dbContext, albumId, userId, cancellationToken);
        response.IsPinned = pin != null;
        response.PinnedAt = pin?.PinnedAt;
        response.PinPosition = pin?.Position;

        return TypedResults.Ok(response);
    }

    private async Task<Results<NoContent, UnauthorizedHttpResult, ForbidHttpResult, NotFound<ApiError>>> DeleteAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        // Verificar permisos de eliminación
        var (hasAccess, _, canDelete, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canDelete)
        {
            return TypedResults.Forbid();
        }

        var album = await dbContext.Albums
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        }

        dbContext.Albums.Remove(album);
        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return TypedResults.NoContent();
    }

    private async Task<Results<NoContent, UnauthorizedHttpResult, NotFound<ApiError>, BadRequest<ApiError>>> LeaveAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        var album = await dbContext.Albums
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        }

        if (album.OwnerId == userId)
        {
            return TypedResults.BadRequest(new ApiError("Owner cannot leave their own album", "owner_cannot_leave"));
        }

        var permission = await dbContext.AlbumPermissions
            .FirstOrDefaultAsync(p => p.AlbumId == albumId && p.UserId == userId, cancellationToken);

        if (permission == null)
        {
            return TypedResults.NotFound(new ApiError("Permission not found for current user", "permission_not_found"));
        }

        dbContext.AlbumPermissions.Remove(permission);
        // A pin on an album you left would resurface if it's shared again.
        await dbContext.AlbumPins
            .Where(p => p.AlbumId == albumId && p.UserId == userId)
            .ExecuteDeleteAsync(cancellationToken);
        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return TypedResults.NoContent();
    }

    private async Task<Results<Ok<AlbumMessageResponse>, UnauthorizedHttpResult, ForbidHttpResult, BadRequest<ApiError>, NotFound<ApiError>>> AddAssetToAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromServices] AssetVisibilityService visibility,
        [FromRoute] Guid albumId,
        [FromBody] AddAssetRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        // Verificar permisos de edición
        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
        {
            return TypedResults.Forbid();
        }

        if (request == null)
        {
            return TypedResults.BadRequest(new ApiError("Request body is required", "request_body_required"));
        }

        var album = await dbContext.Albums
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        }

        if (album.Kind == AlbumKind.Smart)
        {
            return TypedResults.BadRequest(SmartAlbumIsRuleManaged);
        }

        // Adding a photo to an album shares it with everyone who reads the
        // album, so only a photo the caller can read may go in. One they can't
        // is "not found", like an id that doesn't exist.
        var scope = await visibility.GetScopeAsync(userId, cancellationToken);
        var asset = await dbContext.Assets
            .Where(scope.AssetPredicate())
            .FirstOrDefaultAsync(a => a.Id == request.AssetId && a.DeletedAt == null, cancellationToken);

        if (asset == null)
        {
            return TypedResults.NotFound(new ApiError($"Asset with ID {request.AssetId} not found", "asset_not_found"));
        }

        // Check if asset is already in album
        var existing = await dbContext.AlbumAssets
            .AnyAsync(aa => aa.AlbumId == albumId && aa.AssetId == request.AssetId, cancellationToken);

        if (existing)
        {
            return TypedResults.BadRequest(new ApiError("Asset is already in this album", "asset_already_in_album"));
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
        await ForgetRemovalsAsync(dbContext, albumId, [request.AssetId], cancellationToken);

        // If album has no cover, set this as cover
        if (album.CoverAssetId == null)
        {
            album.CoverAssetId = request.AssetId;
        }

        album.UpdatedAt = DateTime.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return TypedResults.Ok(new AlbumMessageResponse("Asset added to album successfully"));
    }

    private async Task<Results<Ok<AddAssetsBatchResponse>, UnauthorizedHttpResult, ForbidHttpResult, NotFound<ApiError>, BadRequest<ApiError>>> AddAssetsToAlbumBatch(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromServices] AssetVisibilityService visibility,
        [FromRoute] Guid albumId,
        [FromBody] AddAssetsBatchRequest request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
            return TypedResults.Forbid();

        var album = await dbContext.Albums.FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);
        if (album == null) return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        if (album.Kind == AlbumKind.Smart) return TypedResults.BadRequest(SmartAlbumIsRuleManaged);

        if (request.AssetIds.Count == 0)
            return TypedResults.BadRequest(new ApiError("AssetIds must not be empty", "asset_ids_required"));

        // Load existing entries to skip duplicates
        var existingIds = await dbContext.AlbumAssets
            .Where(aa => aa.AlbumId == albumId)
            .Select(aa => aa.AssetId)
            .ToHashSetAsync(cancellationToken);

        var toAdd = request.AssetIds.Distinct().Where(id => !existingIds.Contains(id)).ToList();

        // Validate all asset IDs exist and the caller can read them — the album
        // shares them with its readers (see AddAssetToAlbum). The rest are skipped.
        var scope = await visibility.GetScopeAsync(userId, cancellationToken);
        var validIds = await dbContext.Assets
            .Where(scope.AssetPredicate())
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
        await ForgetRemovalsAsync(dbContext, albumId, validIds, cancellationToken);

        if (album.CoverAssetId == null && validIds.Count > 0)
            album.CoverAssetId = validIds[0];

        album.UpdatedAt = DateTime.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return TypedResults.Ok(new AddAssetsBatchResponse(validIds.Count, request.AssetIds.Count - validIds.Count));
    }

    private async Task<Results<NoContent, UnauthorizedHttpResult, ForbidHttpResult, NotFound<ApiError>, BadRequest<ApiError>>> RemoveAssetFromAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromRoute] Guid assetId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        // Verificar permisos de edición
        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
        {
            return TypedResults.Forbid();
        }

        if (await dbContext.Albums.AnyAsync(a => a.Id == albumId && a.Kind == AlbumKind.Smart, cancellationToken))
        {
            return TypedResults.BadRequest(SmartAlbumIsRuleManaged);
        }

        var album = await dbContext.Albums
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        }

        var removed = await RemoveFromAlbumAsync(dbContext, album, [assetId], userId, cancellationToken);
        if (removed.Count == 0)
        {
            return TypedResults.NotFound(new ApiError("Asset not found in this album", "asset_not_in_album"));
        }

        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return TypedResults.NoContent();
    }

    /// <summary>
    /// Takes several photos out of a manual album at once, so a failure can't
    /// leave it half done. Answers with the ids that were in it; the rest
    /// (not in the album) are skipped. They can be restored for
    /// <see cref="RemovalRetention"/> (see RestoreAlbumAssets).
    /// </summary>
    private async Task<Results<Ok<RemoveAssetsBatchResponse>, UnauthorizedHttpResult, ForbidHttpResult, NotFound<ApiError>, BadRequest<ApiError>>> RemoveAssetsFromAlbumBatch(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromBody] AlbumAssetIdsRequest request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
            return TypedResults.Forbid();

        var album = await dbContext.Albums.FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);
        if (album == null) return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        if (album.Kind == AlbumKind.Smart) return TypedResults.BadRequest(SmartAlbumIsRuleManaged);

        if (request.AssetIds.Count == 0)
            return TypedResults.BadRequest(new ApiError("AssetIds must not be empty", "asset_ids_required"));

        var removed = await RemoveFromAlbumAsync(dbContext, album, request.AssetIds, userId, cancellationToken);
        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return TypedResults.Ok(new RemoveAssetsBatchResponse(removed, request.AssetIds.Distinct().Count() - removed.Count));
    }

    /// <summary>
    /// What was taken out of the album in the last <see cref="RemovalRetention"/>,
    /// newest removal first, with who did it — so a removal that went by
    /// unnoticed can still be found and undone. Only for those who can edit
    /// the album, the ones who could put the photos back. Photos sent to the
    /// trash since are left out.
    /// </summary>
    private async Task<Results<Ok<List<RemovedAlbumAssetResponse>>, UnauthorizedHttpResult, ForbidHttpResult>> GetRemovedAlbumAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
            return TypedResults.Forbid();

        var cutoff = DateTime.UtcNow - RemovalRetention;
        var removals = await dbContext.AlbumAssetRemovals
            .AsNoTracking()
            .Include(r => r.Asset)
                .ThenInclude(a => a.Exif)
            .Include(r => r.Asset)
                .ThenInclude(a => a.Thumbnails)
            .Include(r => r.RemovedByUser)
            .Where(r => r.AlbumId == albumId && r.RemovedAt >= cutoff && r.Asset.DeletedAt == null)
            .OrderByDescending(r => r.RemovedAt)
            .ThenBy(r => r.Order)
            .ToListAsync(cancellationToken);

        return TypedResults.Ok(removals.Select(r => new RemovedAlbumAssetResponse
        {
            Asset = ToAlbumItem(r.Asset),
            RemovedAt = r.RemovedAt,
            RemovedByUserId = r.RemovedByUserId,
            RemovedByUsername = r.RemovedByUser?.Username
        }).ToList());
    }

    /// <summary>
    /// Puts recently removed photos back in the album with the place and the
    /// "added" date they had, and as the cover again if they were it and the
    /// album has none now. Skipped: what wasn't removed in the last
    /// <see cref="RemovalRetention"/> and what is in the trash. One already
    /// back in the album (added again by hand) only loses its record.
    /// </summary>
    private async Task<Results<Ok<RestoreAlbumAssetsResponse>, UnauthorizedHttpResult, ForbidHttpResult, NotFound<ApiError>, BadRequest<ApiError>>> RestoreAlbumAssets(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromBody] AlbumAssetIdsRequest request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
            return TypedResults.Forbid();

        var album = await dbContext.Albums.FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);
        if (album == null) return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        if (album.Kind == AlbumKind.Smart) return TypedResults.BadRequest(SmartAlbumIsRuleManaged);

        if (request.AssetIds.Count == 0)
            return TypedResults.BadRequest(new ApiError("AssetIds must not be empty", "asset_ids_required"));

        var ids = request.AssetIds.Distinct().ToList();
        var cutoff = DateTime.UtcNow - RemovalRetention;
        var removals = await dbContext.AlbumAssetRemovals
            .Include(r => r.Asset)
            .Where(r => r.AlbumId == albumId && ids.Contains(r.AssetId) && r.RemovedAt >= cutoff)
            .OrderBy(r => r.Order)
            .ToListAsync(cancellationToken);

        var present = await dbContext.AlbumAssets
            .Where(aa => aa.AlbumId == albumId && ids.Contains(aa.AssetId))
            .Select(aa => aa.AssetId)
            .ToHashSetAsync(cancellationToken);

        var restored = new List<Guid>();
        foreach (var removal in removals)
        {
            // In the trash: keep the record, it can come back once restored from there.
            if (removal.Asset.DeletedAt != null) continue;

            dbContext.AlbumAssetRemovals.Remove(removal);
            if (present.Contains(removal.AssetId)) continue;

            dbContext.AlbumAssets.Add(new AlbumAsset
            {
                AlbumId = albumId,
                AssetId = removal.AssetId,
                Order = removal.Order,
                AddedAt = removal.AddedAt
            });
            restored.Add(removal.AssetId);
            if (removal.WasCover && album.CoverAssetId == null)
                album.CoverAssetId = removal.AssetId;
        }

        if (restored.Count > 0)
        {
            album.UpdatedAt = DateTime.UtcNow;
        }

        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");

        return TypedResults.Ok(new RestoreAlbumAssetsResponse(restored, ids.Count - restored.Count));
    }

    /// <summary>
    /// Takes the given photos out of a manual album and keeps a record of each
    /// (see <see cref="AlbumAssetRemoval"/>), clearing the cover if it was one
    /// of them. Returns the ids that were in the album, in its order; the
    /// caller saves.
    /// </summary>
    private static async Task<List<Guid>> RemoveFromAlbumAsync(
        ApplicationDbContext dbContext,
        Album album,
        IReadOnlyCollection<Guid> assetIds,
        Guid userId,
        CancellationToken cancellationToken)
    {
        var now = DateTime.UtcNow;

        // Expired records go first, before one of them is loaded to be refreshed.
        var cutoff = now - RemovalRetention;
        await dbContext.AlbumAssetRemovals
            .Where(r => r.AlbumId == album.Id && r.RemovedAt < cutoff)
            .ExecuteDeleteAsync(cancellationToken);

        var ids = assetIds.Distinct().ToList();
        var rows = await dbContext.AlbumAssets
            .Where(aa => aa.AlbumId == album.Id && ids.Contains(aa.AssetId))
            .OrderBy(aa => aa.Order)
            .ThenBy(aa => aa.AddedAt)
            .ToListAsync(cancellationToken);
        if (rows.Count == 0) return [];

        var removedIds = rows.Select(aa => aa.AssetId).ToList();
        var previous = await dbContext.AlbumAssetRemovals
            .Where(r => r.AlbumId == album.Id && removedIds.Contains(r.AssetId))
            .ToDictionaryAsync(r => r.AssetId, cancellationToken);

        foreach (var row in rows)
        {
            if (!previous.TryGetValue(row.AssetId, out var removal))
            {
                removal = new AlbumAssetRemoval { AlbumId = album.Id, AssetId = row.AssetId };
                dbContext.AlbumAssetRemovals.Add(removal);
            }
            removal.Order = row.Order;
            removal.AddedAt = row.AddedAt;
            removal.WasCover = album.CoverAssetId == row.AssetId;
            removal.RemovedAt = now;
            removal.RemovedByUserId = userId;
        }

        dbContext.AlbumAssets.RemoveRange(rows);
        if (album.CoverAssetId is { } coverId && removedIds.Contains(coverId))
        {
            album.CoverAssetId = null;
        }
        album.UpdatedAt = now;

        return removedIds;
    }

    /// <summary>The photos are in the album again: what said they were removed no longer holds.</summary>
    private static async Task ForgetRemovalsAsync(
        ApplicationDbContext dbContext,
        Guid albumId,
        IReadOnlyCollection<Guid> assetIds,
        CancellationToken cancellationToken)
    {
        if (assetIds.Count == 0) return;
        var ids = assetIds.ToList();
        var stale = await dbContext.AlbumAssetRemovals
            .Where(r => r.AlbumId == albumId && ids.Contains(r.AssetId))
            .ToListAsync(cancellationToken);
        dbContext.AlbumAssetRemovals.RemoveRange(stale);
    }

    private async Task<Results<Ok<AlbumPinResponse>, UnauthorizedHttpResult, NotFound<ApiError>>> PinAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        // Anyone who can see the album may pin it; an album they can't see is
        // a 404, not a 403, so pinning doesn't leak which ids exist.
        var (hasAccess, _, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess)
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));

        var existing = await dbContext.AlbumPins
            .FirstOrDefaultAsync(p => p.UserId == userId && p.AlbumId == albumId, cancellationToken);
        if (existing == null)
        {
            existing = new AlbumPin
            {
                UserId = userId,
                AlbumId = albumId,
                PinnedAt = DateTime.UtcNow.ToDatabasePrecision(),
                Position = await PinOrder.TopPositionAsync(dbContext, userId, cancellationToken)
            };
            dbContext.AlbumPins.Add(existing);
            try
            {
                await dbContext.SaveChangesAsync(cancellationToken);
            }
            catch (DbUpdateException)
            {
                // A concurrent PUT won the insert: same end state, still idempotent.
                dbContext.ChangeTracker.Clear();
                existing = await dbContext.AlbumPins.AsNoTracking()
                    .FirstAsync(p => p.UserId == userId && p.AlbumId == albumId, cancellationToken);
            }
            cache.Remove($"albums:{userId}");
        }

        return TypedResults.Ok(new AlbumPinResponse { AlbumId = albumId, IsPinned = true, PinnedAt = existing.PinnedAt });
    }

    private async Task<Results<Ok<AlbumPinResponse>, UnauthorizedHttpResult, NotFound<ApiError>>> UnpinAlbum(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var (hasAccess, _, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess)
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));

        var removed = await dbContext.AlbumPins
            .Where(p => p.UserId == userId && p.AlbumId == albumId)
            .ExecuteDeleteAsync(cancellationToken);
        if (removed > 0)
            cache.Remove($"albums:{userId}");

        return TypedResults.Ok(new AlbumPinResponse { AlbumId = albumId, IsPinned = false, PinnedAt = null });
    }

    private async Task<Results<Ok<AlbumResponse>, UnauthorizedHttpResult, ForbidHttpResult, BadRequest<ApiError>, NotFound<ApiError>>> SetAlbumCover(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromRoute] Guid albumId,
        [FromBody] SetCoverRequest? request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        // Verificar permisos de edición
        var (hasAccess, canEdit, _, _) = await CheckAlbumPermissionsAsync(dbContext, albumId, userId, cancellationToken);
        if (!hasAccess || !canEdit)
        {
            return TypedResults.Forbid();
        }

        if (request == null)
        {
            return TypedResults.BadRequest(new ApiError("Request body is required", "request_body_required"));
        }

        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError($"Album with ID {albumId} not found", "album_not_found"));
        }

        // Both kinds keep their members as AlbumAsset rows.
        var inAlbum = await dbContext.AlbumAssets
            .AnyAsync(aa => aa.AlbumId == albumId && aa.AssetId == request.AssetId, cancellationToken);
        if (!inAlbum)
        {
            return TypedResults.BadRequest(new ApiError("Asset must be in the album to set it as cover", "cover_not_in_album"));
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

        var coverHasMediumThumbnail = await dbContext.Assets
            .Where(a => a.Id == request.AssetId)
            .SelectMany(a => a.Thumbnails)
            .AnyAsync(t => t.Size == ThumbnailSize.Medium, cancellationToken);

        var contents = (await LoadContentsAsync(dbContext, new[] { album }, cancellationToken))[album.Id];

        var response = new AlbumResponse
        {
            Id = album.Id,
            Name = album.Name,
            Description = album.Description,
            CreatedAt = album.CreatedAt,
            UpdatedAt = album.UpdatedAt,
            AssetCount = contents.Count,
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
            PreviewThumbnailUrls = PreviewUrls(contents)
        };
        var pin = await GetPinAsync(dbContext, album.Id, userId, cancellationToken);
        response.IsPinned = pin != null;
        response.PinnedAt = pin?.PinnedAt;
        response.PinPosition = pin?.Position;

        return TypedResults.Ok(response);
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
    // Personal to the viewer: pinning never shows up for other members.
    // PinPosition is the user's own order in "Fijados", shared with folder pins.
    public bool IsPinned { get; set; }
    public DateTime? PinnedAt { get; set; }
    public int? PinPosition { get; set; }
    // "Manual" or "Smart" (docs/smart-albums/).
    public string Kind { get; set; } = nameof(AlbumKind.Manual);
}

public class AlbumPinResponse
{
    public Guid AlbumId { get; set; }
    public bool IsPinned { get; set; }
    public DateTime? PinnedAt { get; set; }
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
    // Smart albums only: when present, replaces the rule (owner-only). Null
    // keeps the stored rule, so a plain rename never touches it.
    public Shared.Services.SmartAlbums.SmartRuleNode? SmartRule { get; set; }
}

public class SmartAlbumRuleResponse
{
    public Shared.Services.SmartAlbums.SmartRuleNode Rule { get; set; } = null!;
    public List<SmartRulePersonRef> People { get; set; } = new();
    public List<SmartRuleFolderRef> Folders { get; set; } = new();
}

public class SmartRulePersonRef
{
    public Guid Id { get; set; }
    public string? Name { get; set; }
    public Guid? CoverFaceId { get; set; }
}

public class SmartRuleFolderRef
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public bool IsShared { get; set; }
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

public sealed record AlbumMessageResponse(string Message);

public sealed record AddAssetsBatchResponse(int Added, int Skipped);

public class AlbumAssetIdsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

/// <summary>Removed: the ids taken out, in album order. Skipped: the ones that weren't in it.</summary>
public sealed record RemoveAssetsBatchResponse(List<Guid> Removed, int Skipped);

/// <summary>Restored: the ids back in the album. Skipped: the rest of the request.</summary>
public sealed record RestoreAlbumAssetsResponse(List<Guid> Restored, int Skipped);

public class RemovedAlbumAssetResponse
{
    public TimelineResponse Asset { get; set; } = null!;
    public DateTime RemovedAt { get; set; }
    // Null once that user's account is gone.
    public Guid? RemovedByUserId { get; set; }
    public string? RemovedByUsername { get; set; }
}
