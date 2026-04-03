using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Share;

/// <summary>Returns all public share links sent by the current user.</summary>
public class GetSentSharesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/share/sent", Handle)
            .WithName("GetSentShareLinks")
            .WithTags("Share")
            .WithDescription("Lists all active public share links created by the current user")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        var allLinks = await dbContext.SharedLinks
            .Include(l => l.Asset)
            .Include(l => l.Album)
                .ThenInclude(a => a!.AlbumAssets.OrderBy(aa => aa.Order).Take(1))
            .Where(l => l.CreatedById == userId)
            .OrderByDescending(l => l.CreatedAt)
            .ToListAsync(ct);

        // Filter in C# to avoid Npgsql DateTime kind issues
        var now = DateTime.UtcNow;
        var links = allLinks
            .Where(l => (l.ExpiresAt == null || l.ExpiresAt > now) &&
                        (l.MaxViews == null || l.ViewCount < l.MaxViews))
            .ToList();

        var result = links.Select(l =>
        {
            string? albumCoverUrl = null;
            if (l.Album != null)
            {
                var coverId = l.Album.CoverAssetId ?? l.Album.AlbumAssets.FirstOrDefault()?.AssetId;
                if (coverId.HasValue)
                    albumCoverUrl = $"/api/assets/{coverId}/thumbnail?size=Medium";
            }

            return new SentShareLinkDto
            {
                Token = l.Token,
                CreatedAt = l.CreatedAt,
                ExpiresAt = l.ExpiresAt,
                HasPassword = l.PasswordHash != null,
                AllowDownload = l.AllowDownload,
                MaxViews = l.MaxViews,
                ViewCount = l.ViewCount,
                AssetId = l.AssetId,
                AssetFileName = l.Asset?.FileName,
                AssetType = l.Asset?.Type.ToString(),
                AssetThumbnailUrl = l.AssetId.HasValue ? $"/api/assets/{l.AssetId}/thumbnail?size=Medium" : null,
                AlbumId = l.AlbumId,
                AlbumName = l.Album?.Name,
                AlbumCoverUrl = albumCoverUrl
            };
        }).ToList();

        return Results.Ok(result);
    }
}

public class SentShareLinkDto
{
    public string Token { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public bool HasPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
    public int ViewCount { get; set; }
    public Guid? AssetId { get; set; }
    public string? AssetFileName { get; set; }
    public string? AssetType { get; set; }
    public string? AssetThumbnailUrl { get; set; }
    public Guid? AlbumId { get; set; }
    public string? AlbumName { get; set; }
    public string? AlbumCoverUrl { get; set; }
}
