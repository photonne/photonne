using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Features.Timeline;

namespace Photonne.Server.Api.Features.UnsupportedFiles;

/// <summary>
/// Serves the original bytes of an unsupported file so the user can download it.
/// Mirrors <see cref="Photonne.Server.Api.Features.AssetDetail.AssetContentEndpoint"/>
/// but always streams as a generic download (we don't know how to render these).
/// Scoped to the user's readable folders, same as the listing.
/// </summary>
public class UnsupportedFileContentEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/unsupported-files/{id:guid}/content", Handle)
            .WithName("GetUnsupportedFileContent")
            .WithTags("Assets")
            .WithDescription("Downloads the original bytes of an unsupported file");
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
            .AsNoTracking()
            .FirstOrDefaultAsync(u => u.Id == id, cancellationToken);
        if (file == null)
            return Results.NotFound(new { error = $"Unsupported file {id} not found" });

        // Access scoping — same folders the listing exposes.
        var userRootPath = $"/assets/users/{username}";
        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);
        if (!file.FolderId.HasValue || !allowedFolderIds.Contains(file.FolderId.Value))
            return Results.Forbid();

        var physicalPath = await settingsService.ResolvePhysicalPathAsync(file.FullPath);
        if (!File.Exists(physicalPath))
            return Results.NotFound(new { error = $"File not found at: {physicalPath}" });

        return Results.File(physicalPath, "application/octet-stream",
            fileDownloadName: file.FileName, enableRangeProcessing: true);
    }
}
