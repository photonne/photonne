using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetDetail;

public class UpdateDescriptionEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPatch("/api/assets/{assetId}/description", Handle)
            .WithName("UpdateAssetDescription")
            .WithTags("Assets")
            .WithDescription("Updates the user-defined caption of an asset.")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<UpdateDescriptionResponse>, UnauthorizedHttpResult, NotFound<ApiError>, ForbidHttpResult>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid assetId,
        [FromBody] UpdateDescriptionRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        var asset = await dbContext.Assets
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, ct);

        if (asset == null)
            return TypedResults.NotFound(new ApiError("Asset no encontrado.", "asset_not_found"));

        if (!AssetMetadataPermissions.IsInUserRoot(asset.FullPath, username))
            return TypedResults.Forbid();

        asset.Caption = string.IsNullOrWhiteSpace(request.Caption)
            ? null
            : request.Caption.Trim()[..Math.Min(request.Caption.Trim().Length, 2000)];

        await dbContext.SaveChangesAsync(ct);

        return TypedResults.Ok(new UpdateDescriptionResponse(asset.Caption));
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}

public class UpdateDescriptionRequest
{
    public string? Caption { get; set; }
}

public sealed record UpdateDescriptionResponse(string? Caption);
