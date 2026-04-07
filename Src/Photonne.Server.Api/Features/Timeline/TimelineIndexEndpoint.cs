using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

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
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        try
        {
            var userRootPath = $"/assets/users/{userId}";

            var allFolders = await dbContext.Folders.ToListAsync(cancellationToken);
            var permissions = await dbContext.FolderPermissions
                .Where(p => p.UserId == userId && p.CanRead)
                .ToListAsync(cancellationToken);

            var foldersWithPermissionsSet = await dbContext.FolderPermissions
                .Select(p => p.FolderId)
                .Distinct()
                .ToHashSetAsync(cancellationToken);

            var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();
            foreach (var folder in allFolders)
            {
                if (!foldersWithPermissionsSet.Contains(folder.Id) &&
                    folder.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
                {
                    allowedIds.Add(folder.Id);
                }
            }

            // Group by date (UTC day), return descending
            var index = await dbContext.Assets
                .Where(a => a.DeletedAt == null && !a.IsArchived
                         && a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value))
                .GroupBy(a => a.FileCreatedAt.Date)
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
