using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;

namespace PhotoHub.Server.Api.Features.Share;

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
            .Where(l => l.CreatedById == userId)
            .OrderByDescending(l => l.CreatedAt)
            .ToListAsync(ct);

        // Filter in C# to avoid Npgsql DateTime kind issues
        var now = DateTime.UtcNow;
        var links = allLinks
            .Where(l => (l.ExpiresAt == null || l.ExpiresAt > now) &&
                        (l.MaxViews == null || l.ViewCount < l.MaxViews))
            .ToList();

        var result = links.Select(l => new SentShareLinkDto
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
            AssetThumbnailUrl = l.Asset != null ? $"/api/share/{l.Token}/thumbnail" : null,
            AlbumId = l.AlbumId,
            AlbumName = l.Album?.Name,
            AlbumCoverUrl = l.Album != null ? $"/api/share/{l.Token}/thumbnail" : null
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
