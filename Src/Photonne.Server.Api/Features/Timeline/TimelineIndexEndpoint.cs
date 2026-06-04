using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

public class TimelineIndexEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline/index", Handle)
            .WithName("GetTimelineIndex")
            .WithTags("Assets")
            .WithDescription("Returns a lightweight date/count index of all timeline assets for scrubber positioning")
            .RequireAuthorization();
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        try
        {
            var userRootPath = $"/assets/users/{username}";
            var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
                dbContext, userId, userRootPath, cancellationToken);

            // Group by date (UTC day), return descending. Grouping key is
            // CapturedAt (the timeline sort key) so the scrubber positions
            // match the asset order shown in the timeline.
            var index = await dbContext.Assets
                .Where(a => a.DeletedAt == null && !a.IsArchived
                         && a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value))
                .GroupBy(a => a.CapturedAt.Date)
                .Select(g => new TimelineIndexItemResponse
                {
                    Date = g.Key,
                    Count = g.Count()
                })
                .OrderByDescending(x => x.Date)
                .ToListAsync(cancellationToken);

            return Results.Ok(index);
        }
        catch (Exception ex)
        {
            return Results.Problem(detail: ex.Message, statusCode: StatusCodes.Status500InternalServerError);
        }
    }
}
