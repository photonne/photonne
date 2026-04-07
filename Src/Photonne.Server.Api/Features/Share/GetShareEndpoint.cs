using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Share;

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
        [FromServices] INotificationService notificationService,
        [FromRoute] string token,
        [FromQuery] string? pw,
        CancellationToken ct)
    {
        var link = await dbContext.SharedLinks
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

        // Notify album owner at view milestones
        if (link.AlbumId.HasValue && ShouldNotifyShareView(link.ViewCount))
        {
            var albumName = link.Album?.Name ?? "tu álbum";
            var viewText = link.ViewCount == 1 ? "vez" : "veces";
            await notificationService.CreateAsync(
                link.CreatedById,
                NotificationType.ShareViewed,
                "Álbum compartido visitado",
                $"\"{albumName}\" ha sido visitado {link.ViewCount} {viewText}.",
                $"/albums/{link.AlbumId}");
        }

        // Append password to media URLs so the media endpoints can also validate it
        var pwSuffix = !string.IsNullOrEmpty(pw) ? $"?pw={Uri.EscapeDataString(pw)}" : string.Empty;

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
                    FileCreatedAt = aa.Asset.FileCreatedAt,
                    FileSize = aa.Asset.FileSize,
                    Width = aa.Asset.Exif?.Width,
                    Height = aa.Asset.Exif?.Height,
                    ThumbnailUrl = $"/api/share/{token}/asset/{aa.Asset.Id}/thumbnail{pwSuffix}",
                    ContentUrl = $"/api/share/{token}/asset/{aa.Asset.Id}/content{pwSuffix}"
                }).ToList();

            return Results.Ok(new SharedContentResponse
            {
                Token = token,

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

    private static bool ShouldNotifyShareView(int viewCount)
        => viewCount is 1 or 5 or 10 or 25 or 50 or 100
           || (viewCount > 100 && viewCount % 100 == 0);
}

public class SharedContentResponse
{
    public string Token { get; set; } = string.Empty;
    public bool RequiresPassword { get; set; }
    public bool WrongPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public SharedAlbumDto? Album { get; set; }
    public List<SharedAssetDto>? Assets { get; set; }
    public DateTime? ExpiresAt { get; set; }
}

public class SharedAssetDto
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string Type { get; set; } = string.Empty;
    public DateTime FileCreatedAt { get; set; }
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
