using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

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
        ClaimsPrincipal user,
        [FromQuery] string? q,
        [FromQuery] DateTime? from,
        [FromQuery] DateTime? to,
        [FromQuery] string? folder,
        [FromQuery] int? pageSize,
        [FromQuery(Name = "personId")] Guid[]? personIds,
        [FromQuery(Name = "objectLabel")] string[]? objectLabels,
        [FromQuery(Name = "sceneLabel")] string[]? sceneLabels,
        [FromQuery] string? textQuery,
        CancellationToken ct)
    {
        var effectivePageSize = pageSize is > 0 ? Math.Min(pageSize.Value, 200) : 100;

        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        var hasPersonFilter = personIds is { Length: > 0 };
        var hasObjectFilter = objectLabels is { Length: > 0 };
        var hasSceneFilter = sceneLabels is { Length: > 0 };
        var hasTextFilter = !string.IsNullOrWhiteSpace(textQuery);

        // Require at least one filter to avoid returning everything
        if (string.IsNullOrWhiteSpace(q) && from == null && to == null && string.IsNullOrWhiteSpace(folder) && !hasPersonFilter && !hasObjectFilter && !hasSceneFilter && !hasTextFilter)
            return Results.Ok(new SearchResponse());

        var isAdmin = user.IsInRole("Admin");

        var query = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && !a.IsArchived);

        if (!isAdmin)
        {
            var allowedFolderIds = await GetAllowedFolderIdsAsync(dbContext, userId, ct);
            query = query.Where(a => a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));
        }

        // Text search: filename, full path, user tag names
        if (!string.IsNullOrWhiteSpace(q))
        {
            var tagTypeFilter = Enum.TryParse<AssetTagType>(q, ignoreCase: true, out var matchedType)
                ? (AssetTagType?)matchedType
                : null;

            query = query.Where(a =>
                a.FileName.Contains(q) ||
                a.FullPath.Contains(q) ||
                (a.Caption != null && a.Caption.Contains(q)) ||
                a.UserTags.Any(ut => ut.UserTag.Name.Contains(q)) ||
                a.RecognizedTextLines.Any(t => EF.Functions.ILike(t.Text, "%" + q + "%")) ||
                (tagTypeFilter.HasValue && a.Tags.Any(t => t.TagType == tagTypeFilter.Value)));
        }

        // Date range
        if (from.HasValue)
        {
            var fromUtc = from.Value.ToUniversalTime();
            query = query.Where(a => a.FileCreatedAt >= fromUtc);
        }

        if (to.HasValue)
        {
            // Include the full selected day
            var toUtc = to.Value.ToUniversalTime().Date.AddDays(1);
            query = query.Where(a => a.FileCreatedAt < toUtc);
        }

        // Folder path substring
        if (!string.IsNullOrWhiteSpace(folder))
            query = query.Where(a => a.FullPath.Contains(folder));

        // Objects filter — asset must have at least one detection for EVERY
        // requested label (intersection: "fotos con perro Y coche"). Match is
        // case-insensitive and trimmed so the UI can pass labels verbatim.
        if (hasObjectFilter)
        {
            var labels = objectLabels!
                .Where(l => !string.IsNullOrWhiteSpace(l))
                .Select(l => l.Trim().ToLowerInvariant())
                .Distinct()
                .ToList();

            foreach (var label in labels)
            {
                var captured = label;
                query = query.Where(a => a.DetectedObjects.Any(o => o.Label.ToLower() == captured));
            }
        }

        // Scenes filter — same intersection semantics as objects: asset must
        // match EVERY requested scene label. Useful for "fotos en la playa Y
        // al atardecer" when combined with other criteria.
        if (hasSceneFilter)
        {
            var labels = sceneLabels!
                .Where(l => !string.IsNullOrWhiteSpace(l))
                .Select(l => l.Trim().ToLowerInvariant())
                .Distinct()
                .ToList();

            foreach (var label in labels)
            {
                var captured = label;
                query = query.Where(a => a.ClassifiedScenes.Any(s => s.Label.ToLower() == captured));
            }
        }

        // OCR text filter — match assets whose extracted text matches the
        // websearch tsquery (supports phrases in quotes and the "or"/"-" ops).
        // 'simple' is used for the index, so we use the same configuration on
        // the query side; that keeps numeric tokens (ticket numbers, serials)
        // searchable verbatim and stays language-agnostic.
        if (hasTextFilter)
        {
            var raw = textQuery!.Trim();
            query = query.Where(a => a.RecognizedTextLines.Any(t =>
                EF.Functions.ToTsVector("simple", t.Text)
                    .Matches(EF.Functions.WebSearchToTsQuery("simple", raw))));
        }

        // People filter — asset must have at least one non-rejected face linked
        // to EVERY requested person (intersection: "fotos donde aparezcan A y B").
        if (hasPersonFilter)
        {
            // Validate ownership of the requested persons up front so a forged
            // id can't leak which assets exist.
            var validPersonIds = await dbContext.People.AsNoTracking()
                .Where(p => p.OwnerId == userId && personIds!.Contains(p.Id))
                .Select(p => p.Id)
                .ToListAsync(ct);

            if (validPersonIds.Count == 0)
                return Results.Ok(new SearchResponse());

            foreach (var pid in validPersonIds)
            {
                var capturedPid = pid; // EF captures by reference otherwise
                // Identity is per-user (UserFaceAssignment); the legacy Face
                // identity columns linger but are no longer authoritative.
                query = query.Where(a => a.Faces.Any(f =>
                    dbContext.UserFaceAssignments.Any(uf =>
                        uf.FaceId == f.Id
                        && uf.UserId == userId
                        && uf.PersonId == capturedPid
                        && !uf.IsRejected)));
            }
        }

        var dbItems = await query
            .OrderByDescending(a => a.FileCreatedAt)
            .ThenByDescending(a => a.FileModifiedAt)
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
            FileCreatedAt = a.FileCreatedAt,
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

    private static async Task<HashSet<Guid>> GetAllowedFolderIdsAsync(
        ApplicationDbContext dbContext, Guid userId, CancellationToken ct)
    {
        var userRootPath = $"/assets/users/{userId}";
        var allFolders = await dbContext.Folders.ToListAsync(ct);
        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .ToListAsync(ct);
        var foldersWithPermissions = await dbContext.FolderPermissions
            .Select(p => p.FolderId)
            .Distinct()
            .ToHashSetAsync(ct);

        var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();
        foreach (var f in allFolders)
        {
            if (!foldersWithPermissions.Contains(f.Id) &&
                f.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
            {
                allowedIds.Add(f.Id);
            }
        }

        // Carpetas de bibliotecas externas accesibles
        var accessibleLibraryIds = await dbContext.ExternalLibraryPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .Select(p => p.ExternalLibraryId)
            .ToHashSetAsync(ct);

        foreach (var f in allFolders)
        {
            if (f.ExternalLibraryId.HasValue && accessibleLibraryIds.Contains(f.ExternalLibraryId.Value))
                allowedIds.Add(f.Id);
        }

        return allowedIds;
    }
}

public class SearchResponse
{
    public List<TimelineResponse> Items { get; set; } = new();
    public bool HasMore { get; set; }
}
