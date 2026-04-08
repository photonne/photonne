using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

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

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid assetId,
        [FromBody] UpdateDescriptionRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        var asset = await dbContext.Assets
            .FirstOrDefaultAsync(a => a.Id == assetId && a.DeletedAt == null, ct);

        if (asset == null)
            return Results.NotFound(new { error = "Asset no encontrado." });

        var isAdmin = user.IsInRole("Admin");
        if (!isAdmin && !IsAssetInUserRoot(asset.FullPath, userId))
            return Results.Forbid();

        asset.Caption = string.IsNullOrWhiteSpace(request.Caption)
            ? null
            : request.Caption.Trim()[..Math.Min(request.Caption.Trim().Length, 2000)];

        await dbContext.SaveChangesAsync(ct);

        return Results.Ok(new { caption = asset.Caption });
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }

    private static bool IsAssetInUserRoot(string assetPath, Guid userId)
    {
        var normalized = assetPath.Replace('\\', '/');
        return normalized.Contains($"/users/{userId}/", StringComparison.OrdinalIgnoreCase);
    }
}

public class UpdateDescriptionRequest
{
    public string? Caption { get; set; }
}
