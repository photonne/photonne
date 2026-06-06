using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// Returns a lightweight grid of all timeline assets (type + aspect ratio + date only).
/// The response is streamed section-by-section so the client can begin rendering
/// while the remaining data is still in transit.
/// </summary>
public class TimelineGridEndpoint : IEndpoint
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/timeline/grid", Handle)
            .WithName("GetTimelineGrid")
            .WithTags("Assets")
            .WithDescription("Returns lightweight per-item grid data (type + aspect ratio) for skeleton rendering")
            .RequireAuthorization();
    }

    private async Task Handle(
        HttpContext context,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
        {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            return;
        }
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username))
        {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            return;
        }

        try
        {
            // ── Permission check — shared AllowedFolderCache (inherited grants
            // + personal space + external libraries) ─────────────────────────
            var userRootPath = $"/assets/users/{username}";
            var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
                dbContext, userId, userRootPath, cancellationToken);

            // ── Layout projection — includes Id, dimensions, and dominant color ──
            // Ordering and date grouping use CapturedAt (EXIF date with
            // FileCreatedAt fallback), matching the main timeline endpoint.
            var rawItems = await TimelineQuery.VisibleAssets(dbContext, allowedIds)
                .OrderByDescending(a => a.CapturedAt)
                // Same tiebreaker as the timeline/bucket endpoints, so the
                // structure index the native client prefetches lines up
                // item-for-item with the bucket contents that replace it.
                .ThenByDescending(a => a.FileModifiedAt)
                .Select(a => new
                {
                    a.Id,
                    Year  = a.CapturedAt.Year,
                    Month = a.CapturedAt.Month,
                    Day   = a.CapturedAt.Day,
                    Type  = a.Type,
                    ExifWidth  = a.Exif != null ? a.Exif.Width  : 0,
                    ExifHeight = a.Exif != null ? a.Exif.Height : 0,
                    DominantColor = a.Thumbnails
                        .Where(t => t.Size == ThumbnailSize.Small)
                        .Select(t => t.DominantColor)
                        .FirstOrDefault(),
                    IsReadOnly = a.ExternalLibraryId.HasValue
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
                        Id          = i.Id,
                        Type        = i.Type == AssetType.Video ? "Video" : "Image",
                        AspectRatio = i.ExifWidth > 0 && i.ExifHeight > 0
                            ? Math.Round((double)((double)i.ExifWidth / i.ExifHeight), 4)
                            : 1.0,
                        Date = $"{i.Year:D4}-{i.Month:D2}-{i.Day:D2}",
                        DominantColor = i.DominantColor,
                        Width  = i.ExifWidth ?? 0,
                        Height = i.ExifHeight ?? 0,
                        IsReadOnly = i.IsReadOnly
                    }).ToList()
                })
                .ToList();

            // ── Stream the JSON array section-by-section ──────────────────────────
            context.Response.ContentType = "application/json";

            var body = context.Response.Body;
            var newline = "\n"u8.ToArray();

            await context.Response.WriteAsync("[", cancellationToken);
            for (var i = 0; i < sections.Count; i++)
            {
                if (i > 0) await context.Response.WriteAsync(",", cancellationToken);
                await JsonSerializer.SerializeAsync(body, sections[i], JsonOptions, cancellationToken);
                await body.FlushAsync(cancellationToken);
            }
            await context.Response.WriteAsync("]", cancellationToken);
        }
        catch (Exception ex)
        {
            if (!context.Response.HasStarted)
            {
                context.Response.StatusCode = StatusCodes.Status500InternalServerError;
                await context.Response.WriteAsJsonAsync(new { detail = ex.Message }, cancellationToken);
            }
        }
    }
}
