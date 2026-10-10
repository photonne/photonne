using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Features.Folders;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Organize;

/// <summary>
/// Files every MobileBackup-pending asset matching a condition rule into a
/// target folder in one shot. The rule set is resolved server-side (scoped to
/// <see cref="OrganizeQuery.Pending"/> via <see cref="SmartAlbumResolver.ResolveWithinAsync"/>)
/// so the client never has to round-trip potentially thousands of ids, and the
/// physical move reuses <see cref="FolderAssetMover"/> — the same code path as a
/// manual multi-select move — so collision handling and cache eviction stay in
/// sync. Only the destination needs authorizing: every source asset is the
/// caller's own MobileBackup.
/// </summary>
public class OrganizeRuleMoveEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/organize/rule/move", Handle)
            .WithName("MoveOrganizeRule")
            .WithTags("Assets")
            .WithDescription("Move every MobileBackup-pending asset matching a condition rule into a target folder")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<OrganizeRuleMoveResponse>, UnauthorizedHttpResult, BadRequest<ApiError>, ForbidHttpResult, NotFound<ApiError>>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SmartAlbumResolver resolver,
        [FromServices] SettingsService settingsService,
        [FromServices] IMemoryCache cache,
        ClaimsPrincipal user,
        [FromBody] OrganizeRuleMoveRequest request,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        if (request.Rule is null)
            return TypedResults.BadRequest(new ApiError("A rule is required.", "rule_required"));

        if (!await FoldersEndpoint.CanWriteFolderAsync(dbContext, userId, request.TargetFolderId, user.IsInRole("Admin"), cancellationToken))
            return TypedResults.Forbid();

        var targetFolder = await dbContext.Folders
            .FirstOrDefaultAsync(f => f.Id == request.TargetFolderId, cancellationToken);
        if (targetFolder is null)
            return TypedResults.NotFound(new ApiError("Carpeta destino no encontrada.", "target_folder_not_found"));

        List<Guid> ids;
        try
        {
            var matches = await resolver.ResolveWithinAsync(
                request.Rule, OrganizeQuery.Pending(dbContext, username), userId, cancellationToken);
            ids = await matches.Select(a => a.Id).ToListAsync(cancellationToken);
        }
        catch (SmartRuleException ex)
        {
            return TypedResults.BadRequest(new ApiError(ex.Message, "invalid_rule"));
        }

        if (ids.Count == 0)
            return TypedResults.Ok(new OrganizeRuleMoveResponse { Moved = 0 });

        // Re-load tracked (the pending base is AsNoTracking) so FolderAssetMover's
        // FolderId/path updates persist on SaveChanges.
        var assets = await dbContext.Assets
            .Where(a => ids.Contains(a.Id))
            .ToListAsync(cancellationToken);

        var result = await FolderAssetMover.MoveAsync(
            dbContext, settingsService, cache, userId, assets, targetFolder, cancellationToken,
            request.OrganizeByCaptureYear);

        return TypedResults.Ok(new OrganizeRuleMoveResponse { Moved = result.Moved, YearBreakdown = result.YearBreakdown });
    }

    public class OrganizeRuleMoveRequest
    {
        public SmartRuleNode? Rule { get; set; }
        public Guid TargetFolderId { get; set; }

        /// <summary>When true, file each matched asset into a Year subfolder (e.g.
        /// <c>2026</c>) under the target folder, derived from its capture date.</summary>
        public bool OrganizeByCaptureYear { get; set; }
    }

    public class OrganizeRuleMoveResponse
    {
        public int Moved { get; set; }

        /// <summary>Real per-year distribution of the moved assets (newest first).
        /// Only meaningful when the move used <see cref="OrganizeRuleMoveRequest.OrganizeByCaptureYear"/>.</summary>
        public IReadOnlyList<YearCount> YearBreakdown { get; set; } = [];
    }
}
