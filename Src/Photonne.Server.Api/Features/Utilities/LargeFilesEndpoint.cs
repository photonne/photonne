using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Utilities;

public class LargeFilesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/utilities/large-files", Handle)
            .WithName("GetLargeFiles")
            .WithTags("Utilities")
            .WithDescription("Returns the largest assets for the current user, sorted by file size descending.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromQuery] int count,
        CancellationToken cancellationToken)
    {
        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        if (count <= 0) count = 50;
        if (count > 200) count = 200;

        var userRootPath = $"/assets/users/{userId}";
        var allowedFolderIds = await GetAllowedFolderIdsAsync(dbContext, userId, userRootPath, cancellationToken);

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && !a.IsArchived
                     && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));

        var assets = await query
            .OrderByDescending(a => a.FileSize)
            .Take(count)
            .ToListAsync(cancellationToken);

        var result = assets.Select(a => new TimelineResponse
        {
            Id = a.Id,
            FileName = a.FileName,
            FullPath = a.FullPath,
            FileSize = a.FileSize,
            FileCreatedAt = a.FileCreatedAt,
            FileModifiedAt = a.FileModifiedAt,
            Extension = a.Extension,
            ScannedAt = a.ScannedAt,
            Type = a.Type.ToString(),
            Checksum = a.Checksum,
            HasExif = a.Exif != null,
            HasThumbnails = a.Thumbnails.Any(),
            SyncStatus = AssetSyncStatus.Synced,
            Width = a.Exif?.Width,
            Height = a.Exif?.Height,
            Tags = BuildTagList(a),
            IsFavorite = a.IsFavorite,
            IsArchived = a.IsArchived
        }).ToList();

        return Results.Ok(result);
    }

    private static List<string> BuildTagList(Shared.Models.Asset asset)
    {
        var autoTags = asset.Tags.Select(t => t.TagType.ToString());
        var userTags = asset.UserTags.Select(t => t.UserTag.Name);
        return autoTags.Concat(userTags)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(t => t)
            .ToList();
    }

    private static async Task<HashSet<Guid>> GetAllowedFolderIdsAsync(
        ApplicationDbContext dbContext, Guid userId, string userRootPath, CancellationToken ct)
    {
        var allFolders = await dbContext.Folders.ToListAsync(ct);
        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .ToListAsync(ct);

        var foldersWithPermissionsSet = await dbContext.FolderPermissions
            .Select(p => p.FolderId)
            .Distinct()
            .ToHashSetAsync(ct);

        var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();

        foreach (var folder in allFolders)
        {
            if (!foldersWithPermissionsSet.Contains(folder.Id) &&
                folder.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
            {
                allowedIds.Add(folder.Id);
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

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}
