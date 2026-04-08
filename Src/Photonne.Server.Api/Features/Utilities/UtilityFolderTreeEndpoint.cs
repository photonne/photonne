using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Folders;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Utilities;

/// <summary>
/// Returns the folder tree for the current user only, regardless of admin role.
/// Used by the Utilities section so admins only see their own storage locations.
/// </summary>
public class UtilityFolderTreeEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/utilities/folders/tree", Handle)
            .WithName("GetMyFolderTree")
            .WithTags("Utilities")
            .WithDescription("Returns the folder tree scoped to the current user.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (!FoldersEndpoint.TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        // Always isAdmin=false: utilities show only the current user's folders
        var allFolders = await FoldersEndpoint.GetFoldersForUserAsync(
            dbContext, userId, isAdmin: false, includeAssets: true, cancellationToken);

        var folderIds = allFolders.Select(f => f.Id).ToList();

        var sharedCounts = await dbContext.FolderPermissions
            .Include(p => p.Folder)
            .Where(p => folderIds.Contains(p.FolderId) && p.CanRead)
            .ToListAsync(cancellationToken);

        var folderSharedCounts = sharedCounts
            .GroupBy(p => p.FolderId)
            .Select(g =>
            {
                var samplePath = g.First().Folder.Path;
                var hasOwner = FoldersEndpoint.TryGetUserIdFromPath(samplePath, out var ownerId);
                var count = hasOwner
                    ? g.Count(p => p.UserId != ownerId)
                    : g.Count(p => p.GrantedByUserId != p.UserId);
                return new { FolderId = g.Key, Count = count };
            })
            .ToDictionary(x => x.FolderId, x => x.Count);

        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId)
            .ToListAsync(cancellationToken);

        var folderDict = allFolders.ToDictionary(f => f.Id, f =>
        {
            var userPerm = permissions.FirstOrDefault(p => p.FolderId == f.Id);
            return new FolderResponse
            {
                Id = f.Id,
                Path = f.Path,
                Name = f.Name,
                ParentFolderId = f.ParentFolderId,
                CreatedAt = f.CreatedAt,
                AssetCount = f.Assets.Count(a => FoldersEndpoint.IsBinPath(f.Path) ? a.DeletedAt != null : a.DeletedAt == null),
                FirstAssetId = f.Assets
                    .Where(a => FoldersEndpoint.IsBinPath(f.Path) ? a.DeletedAt != null : a.DeletedAt == null)
                    .OrderByDescending(a => a.ScannedAt).ThenByDescending(a => a.FileModifiedAt)
                    .FirstOrDefault()?.Id,
                PreviewAssetIds = f.Assets
                    .Where(a => FoldersEndpoint.IsBinPath(f.Path) ? a.DeletedAt != null : a.DeletedAt == null)
                    .OrderByDescending(a => a.ScannedAt).ThenByDescending(a => a.FileModifiedAt)
                    .Take(4).Select(a => a.Id).ToList(),
                IsOwner = userPerm?.CanManagePermissions ?? false,
                IsShared = f.Path.StartsWith("/assets/shared", StringComparison.OrdinalIgnoreCase),
                SharedWithCount = folderSharedCounts.TryGetValue(f.Id, out var cnt) ? cnt : 0,
                SubFolders = new List<FolderResponse>()
            };
        });

        var rootFolders = new List<FolderResponse>();
        foreach (var folder in folderDict.Values)
        {
            if (folder.ParentFolderId.HasValue && folderDict.ContainsKey(folder.ParentFolderId.Value))
                folderDict[folder.ParentFolderId.Value].SubFolders.Add(folder);
            else
                rootFolders.Add(folder);
        }

        foreach (var root in rootFolders)
            FoldersEndpoint.UpdateTotalAssetCount(root);

        return Results.Ok(rootFolders);
    }
}
