using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Pins;

/// <summary>
/// The order of the user's "Fijados" (pinned albums and folders together).
/// Pinning itself lives with each kind: PUT/DELETE /api/albums/{id}/pin and
/// /api/folders/{id}/pin.
/// </summary>
public class PinsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/pins")
            .WithTags("Pins")
            .RequireAuthorization();

        group.MapPut("order", ReorderPins)
            .WithName("ReorderPins")
            .WithDescription("Sets the order of the current user's pinned albums and folders");
    }

    private async Task<Results<NoContent, UnauthorizedHttpResult, BadRequest<ApiError>>> ReorderPins(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        [FromBody] ReorderPinsRequest request,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        if (request.Pins.Any(p => p.Kind is not (PinKinds.Album or PinKinds.Folder)))
            return TypedResults.BadRequest(new ApiError("Pin kind must be 'album' or 'folder'", "invalid_pin_kind"));

        var albumPins = await dbContext.AlbumPins
            .Where(p => p.UserId == userId)
            .ToDictionaryAsync(p => p.AlbumId, cancellationToken);
        var folderPins = await dbContext.FolderPins
            .Where(p => p.UserId == userId)
            .ToDictionaryAsync(p => p.FolderId, cancellationToken);

        // The list as sent, skipping what isn't (or is no longer) pinned. Pins the
        // client didn't mention — say, one made on another device a moment ago —
        // keep their current order after the ones it did.
        var ordered = new List<Action<int>>();
        var placed = new HashSet<(string, Guid)>();
        foreach (var pin in request.Pins)
        {
            if (!placed.Add((pin.Kind, pin.Id))) continue;
            if (pin.Kind == PinKinds.Album && albumPins.TryGetValue(pin.Id, out var albumPin))
                ordered.Add(position => albumPin.Position = position);
            else if (pin.Kind == PinKinds.Folder && folderPins.TryGetValue(pin.Id, out var folderPin))
                ordered.Add(position => folderPin.Position = position);
        }

        var rest = albumPins.Values
            .Where(p => !placed.Contains((PinKinds.Album, p.AlbumId)))
            .Select(p => (p.Position, p.PinnedAt, Set: (Action<int>)(position => p.Position = position)))
            .Concat(folderPins.Values
                .Where(p => !placed.Contains((PinKinds.Folder, p.FolderId)))
                .Select(p => (p.Position, p.PinnedAt, Set: (Action<int>)(position => p.Position = position))))
            .OrderBy(p => p.Position)
            .ThenByDescending(p => p.PinnedAt)
            .Select(p => p.Set);
        ordered.AddRange(rest);

        for (var i = 0; i < ordered.Count; i++)
            ordered[i](i);

        await dbContext.SaveChangesAsync(cancellationToken);
        cache.Remove($"albums:{userId}");
        cache.Remove($"folders:list:{userId}");
        cache.Remove($"folders:tree:{userId}");

        return TypedResults.NoContent();
    }
}

public static class PinKinds
{
    public const string Album = "album";
    public const string Folder = "folder";
}

public class ReorderPinsRequest
{
    /// <summary>The pins in their new order, first on top.</summary>
    public List<PinReference> Pins { get; set; } = new();
}

public class PinReference
{
    /// <summary>"album" or "folder".</summary>
    public string Kind { get; set; } = string.Empty;
    public Guid Id { get; set; }
}
