using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Returns a lightweight grid of all timeline assets (type + aspect ratio + date only).
/// Used by the client to render accurate skeleton tiles for unloaded sections before
/// full section data is fetched.
/// </summary>
public class TimelineGridEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline/grid", Handle)
            .WithName("GetTimelineGrid")
            .WithTags("Assets")
            .WithDescription("Returns lightweight per-item grid data (type + aspect ratio) for skeleton rendering")
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
            // ── Permission check (same logic as TimelineIndexEndpoint) ──────────────
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

            // ── Lightweight projection — no thumbnails, no tags, no checksums ──────
            var rawItems = await dbContext.Assets
                .Where(a => a.DeletedAt == null && !a.IsArchived
                         && a.FolderId.HasValue && allowedIds.Contains(a.FolderId.Value))
                .OrderByDescending(a => a.FileCreatedAt)
                .Select(a => new
                {
                    Year  = a.FileCreatedAt.Year,
                    Month = a.FileCreatedAt.Month,
                    Day   = a.FileCreatedAt.Day,
                    Type  = a.Type,
                    ExifWidth  = a.Exif != null ? a.Exif.Width  : 0,
                    ExifHeight = a.Exif != null ? a.Exif.Height : 0
                })
                .ToListAsync(cancellationToken);

            // ── Group by YearMonth in memory ──────────────────────────────────────
            var sections = rawItems
                .GroupBy(i => $"{i.Year:D4}-{i.Month:D2}")
                .Select(g => new TimelineGridSectionResponse
                {
                    YearMonth = g.Key,
                    Items = g.Select(i => new TimelineGridItemResponse
                    {
                        Type        = i.Type == AssetType.Video ? "Video" : "Image",
                        AspectRatio = i.ExifWidth > 0 && i.ExifHeight > 0
                            ? Math.Round((double)((double)i.ExifWidth / i.ExifHeight), 4)
                            : 1.0,
                        Date = $"{i.Year:D4}-{i.Month:D2}-{i.Day:D2}"
                    }).ToList()
                })
                .ToList();

            return Results.Ok(sections);
        }
        catch (Exception ex)
        {
            return Results.Problem(detail: ex.Message, statusCode: StatusCodes.Status500InternalServerError);
        }
    }
}
