using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Videos;

/// <summary>
/// La colección «Vídeos»: todos los vídeos que el usuario ve en su timeline, del
/// más reciente al más antiguo, paginados igual que Favoritos. Parte de
/// <see cref="TimelineQuery.VisibleAssets"/>, así que el clip de una Live Photo
/// (la mitad .mov, etiquetada MotionPhotoPart) no sale como vídeo suelto.
/// </summary>
public class VideosEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/videos", Handle)
            .WithName("GetVideos")
            .WithTags("Assets")
            .WithDescription("Gets the videos visible on the current user's timeline, newest first")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<TimelinePageResponse>, UnauthorizedHttpResult>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int? pageSize,
        CancellationToken cancellationToken)
    {
        var take = pageSize is > 0 ? Math.Min(pageSize.Value, 500) : 150;

        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, $"/assets/users/{username}", cancellationToken);

        var query = TimelineQuery.VisibleAssets(dbContext, allowedIds)
            .Where(a => a.Type == AssetType.Video);

        if (cursor.HasValue)
            query = query.Where(a => a.CapturedAt < cursor.Value.ToUniversalTime());

        var items = await query
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .Take(take + 1)
            .Select(TimelineProjection.ToResponse)
            .ToListAsync(cancellationToken);

        var hasMore = items.Count > take;
        if (hasMore) items.RemoveAt(items.Count - 1);
        await TimelineQuery.HydrateTagsAsync(dbContext, items, cancellationToken);

        return TypedResults.Ok(new TimelinePageResponse
        {
            Items = items,
            HasMore = hasMore,
            NextCursor = hasMore ? items[^1].FileCreatedAt : null
        });
    }
}
