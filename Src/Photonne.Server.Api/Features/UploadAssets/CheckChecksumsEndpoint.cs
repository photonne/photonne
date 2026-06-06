using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.UploadAssets;

public record CheckChecksumsRequest(List<string> Checksums);
public record CheckChecksumsResponse(Dictionary<string, Guid> Existing); // checksum -> assetId

public class CheckChecksumsEndpoint : IEndpoint
{
    private const int MaxChecksumsPerRequest = 1000;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/check-checksums", Handle)
            .WithName("CheckChecksums")
            .WithTags("Assets")
            .WithDescription("Checks which of the provided SHA-256 checksums already exist as assets of the current user. Returns a map of matching checksum -> assetId; absent checksums do not exist.")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        CheckChecksumsRequest request,
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        if (request.Checksums == null || request.Checksums.Count == 0)
            return Results.Ok(new CheckChecksumsResponse([]));

        if (request.Checksums.Count > MaxChecksumsPerRequest)
            return Results.BadRequest($"Too many checksums; maximum is {MaxChecksumsPerRequest} per request");

        var requested = request.Checksums
            .Where(c => !string.IsNullOrWhiteSpace(c))
            .Select(c => c.Trim().ToLowerInvariant())
            .ToHashSet();

        if (requested.Count == 0)
            return Results.Ok(new CheckChecksumsResponse([]));

        var matching = await dbContext.Assets
            .Where(a => a.DeletedAt == null
                     && a.OwnerId == userId
                     && requested.Contains(a.Checksum))
            .Select(a => new { a.Checksum, a.Id })
            .ToListAsync(cancellationToken);

        // Ante duplicados por checksum (no deberían existir tras dedup) nos quedamos con el primero.
        var existing = new Dictionary<string, Guid>();
        foreach (var asset in matching)
            existing.TryAdd(asset.Checksum, asset.Id);

        return Results.Ok(new CheckChecksumsResponse(existing));
    }
}
