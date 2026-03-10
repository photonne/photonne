using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Services;

namespace PhotoHub.Server.Api.Features.Share;

/// <summary>Public endpoint — no auth required.</summary>
public class GetShareEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/share/{token}", Handle)
            .WithName("GetShareLink")
            .WithTags("Share")
            .WithDescription("Returns public share info for a token (no authentication required)");
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] string token,
        [FromQuery] string? pw,
        CancellationToken ct)
    {
        var link = await dbContext.SharedLinks
            .Include(l => l.Asset).ThenInclude(a => a!.Exif)
            .Include(l => l.Asset).ThenInclude(a => a!.Thumbnails)
            .Include(l => l.Album).ThenInclude(a => a!.AlbumAssets).ThenInclude(aa => aa.Asset).ThenInclude(a => a.Exif)
            .Include(l => l.Album).ThenInclude(a => a!.AlbumAssets).ThenInclude(aa => aa.Asset).ThenInclude(a => a.Thumbnails)
            .FirstOrDefaultAsync(l => l.Token == token, ct);

        if (link == null) return Results.NotFound(new { error = "Share link not found" });

        if (link.ExpiresAt.HasValue && link.ExpiresAt.Value < DateTime.UtcNow)
            return Results.Json(new { error = "This link has expired" }, statusCode: 410);

        // Password check — return 200 with requiresPassword so client shows the gate
        if (link.PasswordHash != null)
        {
            if (string.IsNullOrEmpty(pw))
                return Results.Ok(new SharedContentResponse { Token = token, RequiresPassword = true });

            if (!SharePasswordHasher.Verify(pw, link.PasswordHash))
                return Results.Ok(new SharedContentResponse { Token = token, RequiresPassword = true, WrongPassword = true });
        }

        // MaxViews check
        if (link.MaxViews.HasValue && link.ViewCount >= link.MaxViews.Value)
            return Results.Json(new { error = "This link has reached its maximum number of views" }, statusCode: 410);

        // Increment view count
        link.ViewCount++;
        await dbContext.SaveChangesAsync(ct);

        // Append password to media URLs so the media endpoints can also validate it
        var pwSuffix = !string.IsNullOrEmpty(pw) ? $"?pw={Uri.EscapeDataString(pw)}" : string.Empty;

        if (link.AssetId.HasValue && link.Asset != null)
        {
            var asset = link.Asset;
            return Results.Ok(new SharedContentResponse
            {
                Token = token,
                Type = "asset",
                AllowDownload = link.AllowDownload,
                Asset = new SharedAssetDto
                {
                    Id = asset.Id,
                    FileName = asset.FileName,
                    Type = asset.Type.ToString(),
                    CreatedDate = asset.CreatedDate,
                    FileSize = asset.FileSize,
                    Width = asset.Exif?.Width,
                    Height = asset.Exif?.Height,
                    ThumbnailUrl = $"/api/share/{token}/thumbnail{pwSuffix}",
                    ContentUrl = $"/api/share/{token}/content{pwSuffix}"
                },
                ExpiresAt = link.ExpiresAt
            });
        }

        if (link.AlbumId.HasValue && link.Album != null)
        {
            var album = link.Album;
            var assets = album.AlbumAssets
                .OrderBy(aa => aa.Order).ThenBy(aa => aa.AddedAt)
                .Select(aa => new SharedAssetDto
                {
                    Id = aa.Asset.Id,
                    FileName = aa.Asset.FileName,
                    Type = aa.Asset.Type.ToString(),
                    CreatedDate = aa.Asset.CreatedDate,
                    FileSize = aa.Asset.FileSize,
                    Width = aa.Asset.Exif?.Width,
                    Height = aa.Asset.Exif?.Height,
                    ThumbnailUrl = $"/api/share/{token}/asset/{aa.Asset.Id}/thumbnail{pwSuffix}",
                    ContentUrl = $"/api/share/{token}/asset/{aa.Asset.Id}/content{pwSuffix}"
                }).ToList();

            return Results.Ok(new SharedContentResponse
            {
                Token = token,
                Type = "album",
                AllowDownload = link.AllowDownload,
                Album = new SharedAlbumDto
                {
                    Name = album.Name,
                    Description = album.Description,
                    AssetCount = assets.Count,
                    CoverThumbnailUrl = assets.FirstOrDefault()?.ThumbnailUrl
                },
                Assets = assets,
                ExpiresAt = link.ExpiresAt
            });
        }

        return Results.NotFound(new { error = "Shared content not found" });
    }
}

public class SharedContentResponse
{
    public string Token { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty; // "asset" | "album"
    public bool RequiresPassword { get; set; }
    public bool WrongPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public SharedAssetDto? Asset { get; set; }
    public SharedAlbumDto? Album { get; set; }
    public List<SharedAssetDto>? Assets { get; set; }
    public DateTime? ExpiresAt { get; set; }
}

public class SharedAssetDto
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty;
    public DateTime CreatedDate { get; set; }
    public long FileSize { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public string ThumbnailUrl { get; set; } = string.Empty;
    public string ContentUrl { get; set; } = string.Empty;
}

public class SharedAlbumDto
{
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public int AssetCount { get; set; }
    public string? CoverThumbnailUrl { get; set; }
}
