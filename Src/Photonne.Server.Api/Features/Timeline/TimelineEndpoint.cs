using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Dtos;
using Scalar.AspNetCore;

namespace Photonne.Server.Api.Features.Timeline;

public class TimelineEndpoint : IEndpoint
{
    private static List<ScannedFile> _cachedInternalScan = new();
    private static DateTime _internalScanCachedAt = DateTime.MinValue;
    private static readonly SemaphoreSlim _internalScanLock = new(1, 1);
    private static readonly TimeSpan _internalScanTtl = TimeSpan.FromSeconds(60);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline", Handle)
        .CodeSample(
                codeSample: "curl -X GET \"http://localhost:5000/api/assets/timeline\" -H \"Accept: application/json\"",
                label: "cURL Example")
        .WithName("GetTimeline")
        .WithTags("Assets")
        .WithDescription("Gets the timeline of all scanned media files (images and videos)")
        .AddOpenApiOperationTransformer((operation, context, ct) =>
        {
            operation.Summary = "Gets the timeline";
            operation.Description = "Returns all media assets stored in the database, ordered by the most recently scanned first, then by modification date";
            return Task.CompletedTask;
        });
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] DirectoryScanner directoryScanner,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] DateTime? from,
        [FromQuery] int pageSize,
        CancellationToken cancellationToken)
    {
        if (pageSize <= 0) pageSize = 150;
        if (pageSize > 500) pageSize = 500;

        try
        {
            if (!TryGetUserId(user, out var userId))
            {
                return Results.Unauthorized();
            }

            var userRootPath = GetUserRootPath(userId);
            var allowedFolderIds = await GetAllowedFolderIdsForUserAsync(dbContext, userId, userRootPath, cancellationToken);

            var query = dbContext.Assets
                .Include(a => a.Exif)
                .Include(a => a.Thumbnails)
                .Include(a => a.Tags)
                .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
                .Where(a => a.DeletedAt == null && !a.IsArchived
                         && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));

            // Apply cursor (exclusive upper bound on FileCreatedAt)
            if (cursor.HasValue)
            {
                var cursorUtc = cursor.Value.ToUniversalTime();
                query = query.Where(a => a.FileCreatedAt < cursorUtc);
            }

            if (from.HasValue)
            {
                var fromUtc = from.Value.ToUniversalTime();
                query = query.Where(a => a.FileCreatedAt >= fromUtc);
            }

            // Fetch one extra item to determine hasMore
            var dbItems = await query
                .OrderByDescending(a => a.FileCreatedAt)
                .ThenByDescending(a => a.FileModifiedAt)
                .Take(pageSize + 1)
                .ToListAsync(cancellationToken);

            var hasMore = dbItems.Count > pageSize;
            var assets = hasMore ? dbItems.Take(pageSize).ToList() : dbItems;

            // Obtener rutas de carpetas permitidas para filtrar assets no indexados
            // (solo se usa en el scan de filesystem, que solo ocurre en la primera página)
            var allowedFolderPaths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            List<string> normalizedAllowedFolderPaths = new();
            if (!cursor.HasValue)
            {
                allowedFolderPaths = await GetAllowedFolderPathsForUserAsync(dbContext, userId, userRootPath, cancellationToken);
                normalizedAllowedFolderPaths = allowedFolderPaths
                    .Select(NormalizePathForPrefix)
                    .ToList();
            }

            var timelineItems = assets.Select(asset => new TimelineResponse
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
                SyncStatus = AssetSyncStatus.Synced,
                Width = asset.Exif?.Width,
                Height = asset.Exif?.Height,
                DeletedAt = asset.DeletedAt,
                Tags = BuildTagList(asset),
                IsFavorite = asset.IsFavorite,
                IsArchived = asset.IsArchived,
                IsFileMissing = asset.IsFileMissing,
                DominantColor = asset.Thumbnails
                    .FirstOrDefault(t => t.Size == ThumbnailSize.Small)?.DominantColor
            }).ToList();

            // Normalizar rutas existentes en BD para comparación
            var existingPaths = assets
                .Select(a => a.FullPath.Replace('\\', '/').TrimEnd('/'))
                .ToHashSet(StringComparer.OrdinalIgnoreCase);
            
            // Crear un set de nombres de archivos indexados para detectar duplicados
            var indexedFileNames = assets
                .Select(a => a.FileName)
                .ToHashSet(StringComparer.OrdinalIgnoreCase);

            // Copied assets (in internal dir but not indexed) are only scanned on the first page
            // to avoid expensive filesystem scans on every pagination call.
            var internalAssetsPath = settingsService.GetInternalAssetsPath();
            int copiedCount = 0;
            var copiedFileNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var internalScannedFiles = new List<ScannedFile>();
            
            if (!cursor.HasValue && Directory.Exists(internalAssetsPath))
            {
                if (DateTime.UtcNow - _internalScanCachedAt > _internalScanTtl)
                {
                    await _internalScanLock.WaitAsync(cancellationToken);
                    try
                    {
                        if (DateTime.UtcNow - _internalScanCachedAt > _internalScanTtl)
                        {
                            _cachedInternalScan = (await directoryScanner.ScanDirectoryAsync(internalAssetsPath, cancellationToken)).ToList();
                            _internalScanCachedAt = DateTime.UtcNow;
                        }
                    }
                    finally
                    {
                        _internalScanLock.Release();
                    }
                }
                internalScannedFiles = _cachedInternalScan;
                
                // Resolver rutas físicas de assets en BD para comparar
                var existingPhysicalPaths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                foreach (var asset in assets)
                {
                    var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                    if (!string.IsNullOrEmpty(physicalPath))
                    {
                        existingPhysicalPaths.Add(Path.GetFullPath(physicalPath).Replace('\\', '/'));
                    }
                }
                
                // Crear un set de rutas físicas normalizadas para comparación rápida
                var existingPhysicalPathsNormalized = existingPhysicalPaths
                    .Select(p => Path.GetFileName(p))
                    .ToHashSet(StringComparer.OrdinalIgnoreCase);
                
                foreach (var file in internalScannedFiles)
                {
                    var normalizedFilePath = Path.GetFullPath(file.FullPath).Replace('\\', '/');
                    
                    // Si el archivo está en el directorio interno pero no está en BD, está copiado pero no indexado
                    if (!existingPhysicalPaths.Contains(normalizedFilePath))
                    {
                        // Verificar si hay un archivo con el mismo nombre ya indexado (puede tener ruta diferente)
                        var fileName = file.FileName;
                        if (!existingPhysicalPathsNormalized.Contains(fileName))
                        {
                            // Virtualizar la ruta para mostrarla en el timeline
                            var virtualizedPath = await settingsService.VirtualizePathAsync(file.FullPath);
                            
                            // Si no es admin, filtrar por rutas de carpetas permitidas
                            if (TryGetOwnerUserIdFromInternalPath(normalizedFilePath, internalAssetsPath, out var ownerUserId) &&
                                ownerUserId != userId)
                            {
                                continue;
                            }

                            if (TryGetOwnerUserIdFromVirtualPath(virtualizedPath, out var virtualOwnerUserId) &&
                                virtualOwnerUserId != userId)
                            {
                                continue;
                            }

                            var normalizedVirtualPath = NormalizePathForPrefix(virtualizedPath);
                            var normalizedUserRootPath = NormalizePathForPrefix(userRootPath);

                            // Si es una ruta de /assets/users/, debe pertenecer al usuario actual
                            if (normalizedVirtualPath.StartsWith("/assets/users/", StringComparison.OrdinalIgnoreCase) &&
                                !normalizedVirtualPath.StartsWith(normalizedUserRootPath, StringComparison.OrdinalIgnoreCase))
                            {
                                continue;
                            }

                            var isAllowed = false;
                            foreach (var allowedPath in normalizedAllowedFolderPaths)
                            {
                                if (normalizedVirtualPath.StartsWith(allowedPath, StringComparison.OrdinalIgnoreCase))
                                {
                                    isAllowed = true;
                                    break;
                                }
                            }

                            if (!isAllowed) continue;

                            copiedFileNames.Add(fileName);
                            copiedCount++;
                            timelineItems.Add(new TimelineResponse
                            {
                                Id = Guid.Empty,
                                FileName = fileName,
                                FullPath = virtualizedPath,
                                FileSize = file.FileSize,
                                FileCreatedAt = file.FileCreatedAt,
                                FileModifiedAt = file.FileModifiedAt,
                                Extension = file.Extension,
                                ScannedAt = DateTime.MinValue,
                                Type = file.AssetType.ToString(),
                                SyncStatus = AssetSyncStatus.Copied,
                                Width = null,
                                Height = null,
                                Tags = new List<string>()
                            });
                        }
                    }
                }
                Console.WriteLine($"[DEBUG] Identified {copiedCount} copied but not indexed assets to show in timeline");
            }

            // Los assets pendientes del dispositivo se gestionan en la página "Mi Dispositivo" (/device)
            // No se incluyen en el timeline principal para mantenerlo más ligero

            // Re-order by most recent date (preferring CreatedDate but handles cases where only ModifiedDate is available)
            var orderedTimeline = timelineItems
                .OrderByDescending(a => a.SyncStatus == AssetSyncStatus.Pending || a.SyncStatus == AssetSyncStatus.Copied ? a.FileModifiedAt : a.FileCreatedAt)
                .ThenByDescending(a => a.FileName)
                .ToList();

            var nextCursor = hasMore ? assets.Last().FileCreatedAt : (DateTime?)null;

            return Results.Ok(new TimelinePageResponse
            {
                Items = orderedTimeline,
                HasMore = hasMore,
                NextCursor = nextCursor
            });
        }
        catch (Exception ex)
        {
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private static string NormalizePathForPrefix(string path)
    {
        return path.Replace('\\', '/').TrimEnd('/') + "/";
    }

    private static bool TryGetOwnerUserIdFromInternalPath(
        string physicalPath,
        string internalAssetsPath,
        out Guid ownerUserId)
    {
        ownerUserId = Guid.Empty;
        if (string.IsNullOrWhiteSpace(physicalPath) || string.IsNullOrWhiteSpace(internalAssetsPath))
        {
            return false;
        }

        var normalizedFilePath = Path.GetFullPath(physicalPath).Replace('\\', '/');
        var normalizedInternalPath = Path.GetFullPath(internalAssetsPath).Replace('\\', '/').TrimEnd('/');

        if (!normalizedFilePath.StartsWith(normalizedInternalPath, StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        var relativePath = Path.GetRelativePath(normalizedInternalPath, normalizedFilePath)
            .Replace('\\', '/')
            .TrimStart('/');

        if (!relativePath.StartsWith("users/", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        var segments = relativePath.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (segments.Length < 2)
        {
            return false;
        }

        return Guid.TryParse(segments[1], out ownerUserId);
    }

    private static bool TryGetOwnerUserIdFromVirtualPath(string virtualPath, out Guid ownerUserId)
    {
        ownerUserId = Guid.Empty;
        if (string.IsNullOrWhiteSpace(virtualPath))
        {
            return false;
        }

        var normalizedVirtualPath = virtualPath.Replace('\\', '/').TrimStart('/');
        if (!normalizedVirtualPath.StartsWith("assets/users/", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        var segments = normalizedVirtualPath.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (segments.Length < 3)
        {
            return false;
        }

        return Guid.TryParse(segments[2], out ownerUserId);
    }

    private bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(userIdClaim?.Value, out userId);
    }

    private static List<string> BuildTagList(Asset asset)
    {
        var autoTags = asset.Tags.Select(t => t.TagType.ToString());
        var userTags = asset.UserTags.Select(t => t.UserTag.Name);
        return autoTags.Concat(userTags)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(t => t)
            .ToList();
    }

    private string GetUserRootPath(Guid userId)
    {
        return $"/assets/users/{userId}";
    }

    private async Task<HashSet<Guid>> GetAllowedFolderIdsForUserAsync(
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

    private async Task<HashSet<string>> GetAllowedFolderPathsForUserAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string userRootPath,
        CancellationToken ct)
    {
        var allFolders = await dbContext.Folders.ToListAsync(ct);
        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .ToListAsync(ct);

        var foldersWithPermissionsSet = await dbContext.FolderPermissions
            .Select(p => p.FolderId)
            .Distinct()
            .ToHashSetAsync(ct);

        var allowedPaths = permissions
            .Select(p => allFolders.FirstOrDefault(f => f.Id == p.FolderId)?.Path)
            .Where(p => p != null)
            .Select(p => p!.Replace('\\', '/').TrimEnd('/') + "/")
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        // Añadir espacio personal
        allowedPaths.Add(userRootPath.TrimEnd('/') + "/");

        // Añadir carpetas que no tienen permisos explícitos pero están en espacio personal
        foreach (var folder in allFolders)
        {
            if (!foldersWithPermissionsSet.Contains(folder.Id))
            {
                var normalizedPath = folder.Path.Replace('\\', '/').TrimEnd('/') + "/";
                if (normalizedPath.StartsWith(userRootPath.TrimEnd('/') + "/", StringComparison.OrdinalIgnoreCase))
                {
                    allowedPaths.Add(normalizedPath);
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
            {
                var normalizedPath = folder.Path.Replace('\\', '/').TrimEnd('/') + "/";
                allowedPaths.Add(normalizedPath);
            }
        }

        return allowedPaths;
    }
}

