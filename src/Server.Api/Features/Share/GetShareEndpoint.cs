using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Share;

/// <summary>Public endpoint — no auth required.</summary>
public class GetShareEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/share/{token}", Handle)
            .AllowAnonymous()
            .Produces<ApiError>(StatusCodes.Status410Gone)
            .WithName("GetShareLink")
            .WithTags("Share")
            .WithDescription("Returns public share info for a token (no authentication required)");
    }

    private static async Task<Results<Ok<SharedContentResponse>, NotFound<ApiError>, JsonHttpResult<ApiError>>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] INotificationService notificationService,
        [FromServices] SmartAlbumResolver smartResolver,
        [FromRoute] string token,
        [FromQuery] string? pw,
        CancellationToken ct)
    {
        var link = await dbContext.SharedLinks
            .Include(l => l.Album)
            .FirstOrDefaultAsync(l => l.Token == token, ct);

        if (link == null) return TypedResults.NotFound(new ApiError("Share link not found", "share_link_not_found"));

        if (link.ExpiresAt.HasValue && link.ExpiresAt.Value < DateTime.UtcNow)
            return TypedResults.Json(new ApiError("This link has expired", "share_link_expired"), statusCode: StatusCodes.Status410Gone);

        // Password check — return 200 with requiresPassword so client shows the gate
        if (link.PasswordHash != null)
        {
            if (string.IsNullOrEmpty(pw))
                return TypedResults.Ok(new SharedContentResponse { Token = token, RequiresPassword = true });

            if (!SharePasswordHasher.Verify(pw, link.PasswordHash))
                return TypedResults.Ok(new SharedContentResponse { Token = token, RequiresPassword = true, WrongPassword = true });
        }

        // MaxViews check
        if (link.MaxViews.HasValue && link.ViewCount >= link.MaxViews.Value)
            return TypedResults.Json(new ApiError("This link has reached its maximum number of views", "share_link_max_views"), statusCode: StatusCodes.Status410Gone);

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
            // Manual or smart: the same photos the album shows in the app.
            var rows = await (await SharedAlbumAssets.QueryAsync(dbContext, smartResolver, album, ct))
                .Select(a => new
                {
                    a.Id,
                    a.FileName,
                    a.Type,
                    a.FileCreatedAt,
                    a.FileSize,
                    Width = a.Exif != null ? a.Exif.Width : null,
                    Height = a.Exif != null ? a.Exif.Height : null
                })
                .ToListAsync(ct);
            var assets = rows
                .Select(a => new SharedAssetDto
                {
                    Id = a.Id,
                    FileName = a.FileName,
                    Type = a.Type.ToString(),
                    FileCreatedAt = a.FileCreatedAt,
                    FileSize = a.FileSize,
                    Width = a.Width,
                    Height = a.Height,
                    ThumbnailUrl = $"/api/share/{token}/asset/{a.Id}/thumbnail{pwSuffix}",
                    ContentUrl = $"/api/share/{token}/asset/{a.Id}/content{pwSuffix}"
                }).ToList();

            return TypedResults.Ok(new SharedContentResponse
            {
                Token = token,

                AllowDownload = link.AllowDownload,
                AllowUpload = link.AllowUpload,
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

        return TypedResults.NotFound(new ApiError("Shared content not found", "shared_content_not_found"));
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
    public bool AllowUpload { get; set; }
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
