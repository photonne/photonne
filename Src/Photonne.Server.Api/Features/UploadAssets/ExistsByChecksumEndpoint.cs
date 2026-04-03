using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
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

    private static async Task<IResult> Handle(
        string checksum,
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        if (string.IsNullOrWhiteSpace(checksum))
            return Results.BadRequest("Checksum is required");

        var asset = await dbContext.Assets
            .Where(a => a.DeletedAt == null && a.Checksum == checksum)
            .Select(a => new { a.Id })
            .FirstOrDefaultAsync(cancellationToken);

        if (asset == null)
            return Results.NotFound();

        return Results.Ok(new { assetId = asset.Id });
    }
}
