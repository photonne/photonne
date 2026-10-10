using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Utilities;

public class UtilitiesSummaryResponse
{
    // Groups of 2+ visible assets sharing a checksum, as GET /api/utilities/duplicates lists them.
    public int DuplicateGroups { get; set; }
    // Assets inside those groups (every copy, the one kept included).
    public int DuplicateAssets { get; set; }
    // Per group, total minus the largest copy (the one kept) — the same
    // "recoverable" figure the duplicates screen shows.
    public long DuplicateRecoverableBytes { get; set; }
    // Visible assets of at least LargeFileThresholdBytes, and what they weigh.
    public int LargeFilesCount { get; set; }
    public long LargeFilesBytes { get; set; }
    // Rows of GET /api/unsupported-files.
    public int UnsupportedCount { get; set; }
}

/// <summary>
/// Live figures for the Utilities hub, so each entry can say what's waiting in
/// it without opening it. Same scope as the three screens it summarises
/// (<see cref="AllowedFolderCache"/>, not trashed, not archived) and every
/// figure is aggregated in the database — no entity is materialised.
/// </summary>
public class UtilitiesSummaryEndpoint : IEndpoint
{
    // What the hub calls a "large file". The large-files screen has no
    // threshold (it lists the N biggest), but a top-N count would always read
    // "50 files" on any real library, so the hub counts assets of 100 MB or more.
    public const long LargeFileThresholdBytes = 100L * 1024 * 1024;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/utilities/summary", Handle)
            .WithName("GetUtilitiesSummary")
            .WithTags("Utilities")
            .WithDescription("Returns duplicate, large-file and unsupported-file counts for the current user's accessible assets.")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<UtilitiesSummaryResponse>, UnauthorizedHttpResult>> Handle(
        ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        var userRootPath = $"/assets/users/{username}";
        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);

        var visible = dbContext.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived
                     && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));

        // One row per duplicate group (three numbers each), summed here.
        var duplicateGroups = await visible
            .Where(a => a.Checksum != null && a.Checksum != "")
            .GroupBy(a => a.Checksum)
            .Where(g => g.Count() > 1)
            .Select(g => new
            {
                Count = g.Count(),
                Recoverable = g.Sum(a => a.FileSize) - g.Max(a => a.FileSize)
            })
            .ToListAsync(cancellationToken);

        // COUNT + SUM in one row; GroupBy on a constant is EF's idiom for a
        // single aggregate row (none when there's nothing over the threshold).
        var large = await visible
            .Where(a => a.FileSize >= LargeFileThresholdBytes)
            .GroupBy(_ => 1)
            .Select(g => new { Count = g.Count(), Bytes = g.Sum(a => a.FileSize) })
            .FirstOrDefaultAsync(cancellationToken);

        var unsupportedCount = await dbContext.UnsupportedFiles
            .AsNoTracking()
            .CountAsync(u => u.FolderId.HasValue && allowedFolderIds.Contains(u.FolderId.Value), cancellationToken);

        return TypedResults.Ok(new UtilitiesSummaryResponse
        {
            DuplicateGroups = duplicateGroups.Count,
            DuplicateAssets = duplicateGroups.Sum(g => g.Count),
            DuplicateRecoverableBytes = duplicateGroups.Sum(g => g.Recoverable),
            LargeFilesCount = large?.Count ?? 0,
            LargeFilesBytes = large?.Bytes ?? 0,
            UnsupportedCount = unsupportedCount
        });
    }
}
