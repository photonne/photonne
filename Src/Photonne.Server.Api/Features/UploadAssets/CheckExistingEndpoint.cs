using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.UploadAssets;

public record CheckExistingRequest(List<CheckExistingItem> Files);
public record CheckExistingItem(string Name, long Size);
public record CheckExistingResponse(HashSet<string> ExistingKeys); // "name|size"

public class CheckExistingEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/check-existing", Handle)
            .WithName("CheckExistingAssets")
            .WithTags("Assets")
            .WithDescription("Checks which of the provided files (by name+size) already exist for the current user")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        CheckExistingRequest request,
        ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        if (request.Files == null || request.Files.Count == 0)
            return Results.Ok(new CheckExistingResponse([]));

        var requestedNames = request.Files.Select(f => f.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var requestedSizes = request.Files.ToDictionary(
            f => f.Name,
            f => f.Size,
            StringComparer.OrdinalIgnoreCase);

        // Obtener los assets del usuario que coincidan por nombre
        var userRootPath = $"/assets/users/{userId}";
        var matching = await dbContext.Assets
            .Where(a => a.DeletedAt == null
                     && a.OwnerId == userId
                     && requestedNames.Contains(a.FileName))
            .Select(a => new { a.FileName, a.FileSize })
            .ToListAsync(cancellationToken);

        // Filtrar por nombre+tamaño y devolver las claves que coinciden
        var existingKeys = matching
            .Where(a => requestedSizes.TryGetValue(a.FileName, out var size) && size == a.FileSize)
            .Select(a => $"{a.FileName}|{a.FileSize}")
            .ToHashSet();

        return Results.Ok(new CheckExistingResponse(existingKeys));
    }
}
