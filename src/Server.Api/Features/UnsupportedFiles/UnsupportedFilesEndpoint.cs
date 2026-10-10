using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
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
    // Whether the caller may delete the file from disk — see
    // UnsupportedFileDeleteEndpoint. Clients gate the delete button on it.
    public bool CanDelete { get; set; }
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
/// the "Archivos no compatibles" catalogue. Scoped to the folders the user can read,
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

    private static async Task<Results<Ok<UnsupportedFilesPageResponse>, UnauthorizedHttpResult>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        [FromQuery] DateTime? cursor,
        [FromQuery] int pageSize,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

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
            .ToListAsync(cancellationToken);

        var hasMore = page.Count > pageSize;
        var rows = hasMore ? page.Take(pageSize).ToList() : page;
        var nextCursor = hasMore ? rows.Last().DiscoveredAt : (DateTime?)null;

        // Many rows share a folder, so the shared-space check runs once per folder.
        var isAdmin = user.IsInRole("Admin");
        var deletableFolders = new Dictionary<Guid, bool>();
        var items = new List<UnsupportedFileResponse>(rows.Count);
        foreach (var u in rows)
        {
            items.Add(new UnsupportedFileResponse
            {
                Id = u.Id,
                FileName = u.FileName,
                FullPath = u.FullPath,
                FileSize = u.FileSize,
                Extension = u.Extension,
                FileCreatedAt = u.FileCreatedAt,
                DiscoveredAt = u.DiscoveredAt,
                CanDelete = await UnsupportedFileDeleteEndpoint.CanDeleteAsync(
                    dbContext, u, userId, username, isAdmin, deletableFolders, cancellationToken)
            });
        }

        return TypedResults.Ok(new UnsupportedFilesPageResponse
        {
            Items = items,
            HasMore = hasMore,
            NextCursor = nextCursor
        });
    }
}
