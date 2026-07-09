using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Search;

public class SearchEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/search", Handle)
            .WithName("SearchAssets")
            .WithTags("Assets")
            .WithDescription("Search assets by filename, path, tags or date range")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        [FromQuery] string? q,
        [FromQuery] DateTime? from,
        [FromQuery] DateTime? to,
        [FromQuery] string? folder,
        [FromQuery] int? pageSize,
        [FromQuery] int? offset,
        [FromQuery(Name = "personId")] Guid[]? personIds,
        [FromQuery(Name = "objectLabel")] string[]? objectLabels,
        [FromQuery(Name = "sceneLabel")] string[]? sceneLabels,
        [FromQuery] string? textQuery,
        CancellationToken ct)
    {
        var effectivePageSize = pageSize is > 0 ? Math.Min(pageSize.Value, 200) : 100;
        // Cap offset so a forged query can't force the server to materialize
        // an arbitrarily deep window. 10k matches the practical scroll depth
        // we'd ever ask the user to traverse without re-filtering.
        var effectiveOffset = offset is > 0 ? Math.Min(offset.Value, 10_000) : 0;

        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var hasPersonFilter = personIds is { Length: > 0 };
        var hasObjectFilter = objectLabels is { Length: > 0 };
        var hasSceneFilter = sceneLabels is { Length: > 0 };
        var hasTextFilter = !string.IsNullOrWhiteSpace(textQuery);

        // Require at least one filter to avoid returning everything
        if (string.IsNullOrWhiteSpace(q) && from == null && to == null && string.IsNullOrWhiteSpace(folder) && !hasPersonFilter && !hasObjectFilter && !hasSceneFilter && !hasTextFilter)
            return Results.Ok(new SearchResponse());

        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, $"/assets/users/{username}", ct);

        // Visibility gate stays here (search scopes by allowed folders); only
        // the match criteria are delegated to the shared AssetQueryBuilder so
        // search and smart albums filter identically (docs/smart-albums/).
        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                     && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));

        // People filter is per-user; validate ownership of the requested persons
        // up front so a forged id can't leak which assets exist. Passing an empty
        // set to the builder would AND nothing, so short-circuit to no results.
        IReadOnlyList<Guid>? validPersonIds = null;
        if (hasPersonFilter)
        {
            validPersonIds = await dbContext.People.AsNoTracking()
                .Where(p => p.OwnerId == userId && personIds!.Contains(p.Id))
                .Select(p => p.Id)
                .ToListAsync(ct);

            if (validPersonIds.Count == 0)
                return Results.Ok(new SearchResponse());
        }

        query = AssetQueryBuilder.Apply(query, new AssetFilter
        {
            Text = q,
            From = from,
            To = to,
            FolderPath = folder,
            ObjectLabels = objectLabels,
            SceneLabels = sceneLabels,
            OcrText = textQuery,
            PersonIds = validPersonIds
        }, dbContext, userId);

        var dbItems = await query
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .ThenBy(a => a.Id)
            .Skip(effectiveOffset)
            .Take(effectivePageSize + 1)
            .ToListAsync(ct);

        var hasMore = dbItems.Count > effectivePageSize;
        var assets = hasMore ? dbItems.Take(effectivePageSize).ToList() : dbItems;

        var items = assets.Select(a => new TimelineResponse
        {
            Id = a.Id,
            FileName = a.FileName,
            FullPath = a.FullPath,
            FileSize = a.FileSize,
            FileCreatedAt = a.CapturedAt,
            FileModifiedAt = a.FileModifiedAt,
            Extension = a.Extension,
            ScannedAt = a.ScannedAt,
            Type = a.Type.ToString(),
            Checksum = a.Checksum,
            HasExif = a.Exif != null,
            HasThumbnails = a.Thumbnails.Any(),
            SyncStatus = AssetSyncStatus.Synced,
            Width = a.Exif?.Width,
            Height = a.Exif?.Height,
            Tags = BuildTagList(a),
            IsFavorite = a.IsFavorite,
            IsFileMissing = a.IsFileMissing,
            IsReadOnly = a.ExternalLibraryId.HasValue
        }).ToList();

        return Results.Ok(new SearchResponse { Items = items, HasMore = hasMore });
    }

    private static List<string> BuildTagList(Asset asset)
    {
        var autoTags = asset.Tags.Select(t => t.TagType.ToString());
        var userTags = asset.UserTags.Select(t => t.UserTag.Name);
        return autoTags.Concat(userTags)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(t => t)
            .ToList();
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }

}

public class SearchResponse
{
    public List<TimelineResponse> Items { get; set; } = new();
    public bool HasMore { get; set; }
}
