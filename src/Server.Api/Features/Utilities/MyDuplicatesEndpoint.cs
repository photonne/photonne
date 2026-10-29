using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Utilities;

public class MyDuplicatesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/utilities/duplicates", Handle)
            .WithName("GetMyDuplicates")
            .WithTags("Utilities")
            .WithDescription("Returns duplicate asset groups for the current user's accessible assets.")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<List<UserDuplicateGroupResponse>>, UnauthorizedHttpResult>> Handle(
        ApplicationDbContext dbContext,
        AllowedFolderCache allowedFolders,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (!TryGetUserId(user, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        var userRootPath = $"/assets/users/{username}";
        var allowedFolderIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, userRootPath, cancellationToken);

        var visible = dbContext.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived && a.Checksum != null && a.Checksum != ""
                     && a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));

        // The checksums with 2+ copies are found in the database, and only
        // those assets are read, projected to what the response needs.
        // Materialising every visible asset with its EXIF, thumbnails and tags
        // to group them here took longer than the clients' socket timeout on
        // large libraries.
        var duplicateChecksums = visible
            .GroupBy(a => a.Checksum)
            .Where(g => g.Count() > 1)
            .Select(g => g.Key);

        var rows = await visible
            .Where(a => duplicateChecksums.Contains(a.Checksum))
            .Select(a => new DuplicateRow
            {
                Id = a.Id,
                FileName = a.FileName,
                FullPath = a.FullPath,
                FileSize = a.FileSize,
                CapturedAt = a.CapturedAt,
                FileModifiedAt = a.FileModifiedAt,
                Extension = a.Extension,
                ScannedAt = a.ScannedAt,
                Type = a.Type,
                Checksum = a.Checksum,
                HasExif = a.Exif != null,
                HasThumbnails = a.Thumbnails.Any(),
                Width = a.Exif != null ? a.Exif.Width : null,
                Height = a.Exif != null ? a.Exif.Height : null,
                AutoTags = a.Tags.Select(t => t.TagType).ToList(),
                UserTags = a.UserTags.Select(ut => ut.UserTag.Name).ToList(),
                IsFavorite = a.IsFavorite,
                IsArchived = a.IsArchived
            })
            .AsSplitQuery()
            .ToListAsync(cancellationToken);

        var groups = rows
            .GroupBy(a => a.Checksum)
            .Select(g => new UserDuplicateGroupResponse
            {
                Hash = g.Key,
                TotalSize = g.Sum(a => a.FileSize),
                Assets = g.Select(MapToResponse).ToList()
            })
            .OrderByDescending(g => g.TotalSize)
            .ToList();

        return TypedResults.Ok(groups);
    }

    private sealed class DuplicateRow
    {
        public Guid Id { get; init; }
        public string FileName { get; init; } = string.Empty;
        public string FullPath { get; init; } = string.Empty;
        public long FileSize { get; init; }
        public DateTime CapturedAt { get; init; }
        public DateTime FileModifiedAt { get; init; }
        public string Extension { get; init; } = string.Empty;
        public DateTime ScannedAt { get; init; }
        public Shared.Models.AssetType Type { get; init; }
        public string Checksum { get; init; } = string.Empty;
        public bool HasExif { get; init; }
        public bool HasThumbnails { get; init; }
        public int? Width { get; init; }
        public int? Height { get; init; }
        public List<Shared.Models.AssetTagType> AutoTags { get; init; } = new();
        public List<string> UserTags { get; init; } = new();
        public bool IsFavorite { get; init; }
        public bool IsArchived { get; init; }
    }

    private static TimelineResponse MapToResponse(DuplicateRow a) => new()
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
        HasExif = a.HasExif,
        HasThumbnails = a.HasThumbnails,
        SyncStatus = AssetSyncStatus.Synced,
        Width = a.Width,
        Height = a.Height,
        Tags = BuildTagList(a),
        IsFavorite = a.IsFavorite,
        IsArchived = a.IsArchived
    };

    private static List<string> BuildTagList(DuplicateRow asset)
    {
        var autoTags = asset.AutoTags.Select(t => t.ToString());
        return autoTags.Concat(asset.UserTags)
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
