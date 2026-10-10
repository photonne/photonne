using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.UploadAssets;

public class ExistsByChecksumEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/exists/{checksum}", Handle)
            .WithName("ExistsByChecksum")
            .WithTags("Assets")
            .WithDescription("Returns 200+assetId if an asset with the given SHA-256 checksum exists for the current user, 404 otherwise")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<ExistsByChecksumResponse>, UnauthorizedHttpResult, BadRequest<ApiError>, NotFound>> Handle(
        string checksum,
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        if (string.IsNullOrWhiteSpace(checksum))
            return TypedResults.BadRequest(new ApiError("Checksum is required", "checksum_required"));

        var asset = await dbContext.Assets
            .Where(a => a.DeletedAt == null && a.Checksum == checksum)
            .Select(a => new { a.Id })
            .FirstOrDefaultAsync(cancellationToken);

        if (asset == null)
            return TypedResults.NotFound();

        return TypedResults.Ok(new ExistsByChecksumResponse(asset.Id));
    }
}

public sealed record ExistsByChecksumResponse(Guid AssetId);
