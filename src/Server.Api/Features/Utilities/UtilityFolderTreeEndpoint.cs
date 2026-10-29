using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
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

    private static async Task<Results<Ok<List<FolderResponse>>, UnauthorizedHttpResult>> Handle(
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (!FoldersEndpoint.TryGetUserId(user, out var userId))
            return TypedResults.Unauthorized();

        var isAdmin = user.IsInRole("Admin");
        var allFolders = await FoldersEndpoint.GetFoldersForUserAsync(
            dbContext, userId, isAdmin, cancellationToken);
        var summaries = await FoldersEndpoint.GetFolderAssetSummariesAsync(
            dbContext, allFolders, binAware: true, cancellationToken);

        var folderIds = allFolders.Select(f => f.Id).ToList();

        var sharedCounts = await dbContext.FolderPermissions
            .Include(p => p.Folder)
            .Where(p => folderIds.Contains(p.FolderId) && p.CanRead)
            .ToListAsync(cancellationToken);

        var usernameToIdMap = await dbContext.Users
            .AsNoTracking()
            .Select(u => new { u.Username, u.Id })
            .ToDictionaryAsync(u => u.Username, u => u.Id, StringComparer.OrdinalIgnoreCase, cancellationToken);

        var folderSharedCounts = sharedCounts
            .GroupBy(p => p.FolderId)
            .Select(g =>
            {
                var samplePath = g.First().Folder.Path;
                var hasOwner = FoldersEndpoint.TryGetUserIdFromPath(samplePath, usernameToIdMap, out var ownerId);
                var count = hasOwner
                    ? g.Count(p => p.UserId != ownerId)
                    : g.Count(p => p.GrantedByUserId != p.UserId);
                return new { FolderId = g.Key, Count = count };
            })
            .ToDictionary(x => x.FolderId, x => x.Count);

        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId)
            .ToListAsync(cancellationToken);

        var writableGrantIds = permissions.Where(p => p.CanWrite).Select(p => p.FolderId).ToHashSet();
        var deletableGrantIds = permissions.Where(p => p.CanDelete).Select(p => p.FolderId).ToHashSet();
        var folderById = allFolders.ToDictionary(f => f.Id);

        var folderDict = allFolders.ToDictionary(f => f.Id, f =>
        {
            var userPerm = permissions.FirstOrDefault(p => p.FolderId == f.Id);
            var summary = FoldersEndpoint.SummaryOf(summaries, f.Id);
            return new FolderResponse
            {
                Id = f.Id,
                Path = f.Path,
                Name = f.Name,
                ParentFolderId = f.ParentFolderId,
                CreatedAt = f.CreatedAt,
                AssetCount = summary.Count,
                FirstAssetId = summary.FirstId,
                PreviewAssetIds = summary.PreviewIds,
                IsOwner = FoldersEndpoint.OwnsByPath(f.Path, usernameToIdMap, userId)
                    || (isAdmin && FoldersEndpoint.IsInSharedSpace(f.Path))
                    || (userPerm?.CanManagePermissions ?? false),
                CanWrite = FoldersEndpoint.HasFolderAccessInMemory(f, usernameToIdMap, userId, isAdmin, writableGrantIds, folderById),
                CanDelete = FoldersEndpoint.HasFolderAccessInMemory(f, usernameToIdMap, userId, isAdmin, deletableGrantIds, folderById),
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

        return TypedResults.Ok(rootFolders);
    }
}
