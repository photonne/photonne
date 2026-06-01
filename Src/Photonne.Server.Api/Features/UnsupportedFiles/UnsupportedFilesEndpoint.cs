using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Features.Timeline;

namespace Photonne.Server.Api.Features.UnsupportedFiles;

public class UnsupportedFileResponse
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public string Extension { get; set; } = string.Empty;
    public DateTime FileCreatedAt { get; set; }
    public DateTime DiscoveredAt { get; set; }
}

public class UnsupportedFilesPageResponse
{
    public List<UnsupportedFileResponse> Items { get; set; } = new();
    public bool HasMore { get; set; }
    // DiscoveredAt of the oldest item returned; pass as `cursor` for the next page.
    public DateTime? NextCursor { get; set; }
}

/// <summary>
/// Lists files found on disk whose extension isn't a recognised image/video —
/// the "Otros archivos" catalogue. Scoped to the folders the user can read,
/// exactly like the timeline (<see cref="AllowedFolderCache"/>). Paginated by
/// DiscoveredAt (newest first) with an exclusive cursor.
/// </summary>
public class UnsupportedFilesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/unsupported-files", Handle)
            .WithName("GetUnsupportedFiles")
            .WithTags("Assets")
            .WithDescription("Lists unsupported (non-media) files found on disk for the current user")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int pageSize,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (pageSize <= 0) pageSize = 150;
        if (pageSize > 500) pageSize = 500;

        var userRootPath = $"/assets/users/{username}";
        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);

        var query = dbContext.UnsupportedFiles
            .AsNoTracking()
            .Where(u => u.FolderId.HasValue && allowedFolderIds.Contains(u.FolderId.Value));

        if (cursor.HasValue)
        {
            var cursorUtc = cursor.Value.ToUniversalTime();
            query = query.Where(u => u.DiscoveredAt < cursorUtc);
        }

        var page = await query
            .OrderByDescending(u => u.DiscoveredAt)
            .Take(pageSize + 1)
            .Select(u => new UnsupportedFileResponse
            {
                Id = u.Id,
                FileName = u.FileName,
                FullPath = u.FullPath,
                FileSize = u.FileSize,
                Extension = u.Extension,
                FileCreatedAt = u.FileCreatedAt,
                DiscoveredAt = u.DiscoveredAt
            })
            .ToListAsync(cancellationToken);

        var hasMore = page.Count > pageSize;
        var items = hasMore ? page.Take(pageSize).ToList() : page;
        var nextCursor = hasMore ? items.Last().DiscoveredAt : (DateTime?)null;

        return Results.Ok(new UnsupportedFilesPageResponse
        {
            Items = items,
            HasMore = hasMore,
            NextCursor = nextCursor
        });
    }
}
