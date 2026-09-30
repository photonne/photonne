using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Assets;
using Photonne.Server.Api.Features.Folders;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.UnsupportedFiles;

/// <summary>
/// Deletes an unsupported file from disk and from the catalogue. Permanent:
/// these aren't Assets, so there is no trash to move them to. Same authorization
/// as trashing an asset — your own personal space, or a shared folder you hold
/// CanDelete on; external libraries are read-only.
/// </summary>
public class UnsupportedFileDeleteEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapDelete("/api/unsupported-files/{id:guid}", Handle)
            .WithName("DeleteUnsupportedFile")
            .WithTags("Assets")
            .WithDescription("Permanently deletes an unsupported file from disk")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        [FromRoute] Guid id,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var file = await dbContext.UnsupportedFiles
            .FirstOrDefaultAsync(u => u.Id == id, cancellationToken);
        if (file == null)
            return Results.NotFound(new { error = $"Unsupported file {id} not found" });

        // Access scoping — same folders the listing exposes — before the
        // stricter delete rule, so a file the caller can't even see is a 403
        // either way.
        var userRootPath = $"/assets/users/{username}";
        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);
        if (!file.FolderId.HasValue || !allowedFolderIds.Contains(file.FolderId.Value))
            return Results.Forbid();

        if (!await CanDeleteAsync(dbContext, file, userId, username, user.IsInRole("Admin"),
                new Dictionary<Guid, bool>(), cancellationToken))
            return Results.Forbid();

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(file.FullPath);
        try
        {
            // Already gone from disk is fine: the row is what's left to clean up.
            if (File.Exists(physicalPath))
                File.Delete(physicalPath);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Keep the row: the file is still there.
            return Results.Problem(
                detail: $"No se pudo borrar el archivo del disco: {ex.Message}",
                statusCode: StatusCodes.Status500InternalServerError);
        }

        dbContext.UnsupportedFiles.Remove(file);
        await dbContext.SaveChangesAsync(cancellationToken);
        return Results.NoContent();
    }

    /// <summary>
    /// Mirrors <see cref="AssetsEndpoint.TrashOrDeleteAssetsAsync"/>'s partition:
    /// personal-space files belong to their user; shared-space files need
    /// CanDelete on their folder; anything else (another member's space, an
    /// external library Photonne doesn't own) can't be deleted.
    /// <paramref name="folderCache"/> memoizes the per-folder answer across a page.
    /// </summary>
    internal static async Task<bool> CanDeleteAsync(
        ApplicationDbContext dbContext,
        UnsupportedFile file,
        Guid userId,
        string username,
        bool isAdmin,
        Dictionary<Guid, bool> folderCache,
        CancellationToken ct)
    {
        if (file.ExternalLibraryId.HasValue) return false;
        if (AssetsEndpoint.IsAssetInUserRoot(file.FullPath, username)) return true;
        if (!FoldersEndpoint.IsInSharedSpace(file.FullPath)) return false;
        if (file.FolderId is not Guid folderId) return false;

        if (!folderCache.TryGetValue(folderId, out var canDelete))
        {
            canDelete = await FoldersEndpoint.CanDeleteFolderAsync(dbContext, userId, folderId, isAdmin, ct);
            folderCache[folderId] = canDelete;
        }
        return canDelete;
    }
}
