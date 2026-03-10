using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Models;
using PhotoHub.Server.Api.Shared.Services;

namespace PhotoHub.Server.Api.Features.Share;

public class CreateShareEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/share", Handle)
            .WithName("CreateShareLink")
            .WithTags("Share")
            .WithDescription("Creates a public share link for an asset or album")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromBody] CreateShareRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        if (request.AssetId == null && request.AlbumId == null)
            return Results.BadRequest(new { error = "AssetId or AlbumId is required" });

        // Verify ownership / access
        if (request.AssetId.HasValue)
        {
            var exists = await dbContext.Assets.AnyAsync(a => a.Id == request.AssetId && a.DeletedAt == null, ct);
            if (!exists) return Results.NotFound(new { error = "Asset not found" });
        }
        else if (request.AlbumId.HasValue)
        {
            var isAdmin = user.IsInRole("Admin");
            var album = await dbContext.Albums
                .Include(a => a.Permissions)
                .FirstOrDefaultAsync(a => a.Id == request.AlbumId, ct);
            if (album == null) return Results.NotFound(new { error = "Album not found" });
            var canShare = isAdmin || album.OwnerId == userId ||
                           album.Permissions.Any(p => p.UserId == userId && p.CanEdit);
            if (!canShare) return Results.Forbid();
        }

        var link = new SharedLink
        {
            Token = Guid.NewGuid().ToString("N"),
            AssetId = request.AssetId,
            AlbumId = request.AlbumId,
            CreatedById = userId,
            ExpiresAt = request.ExpiresAt,
            PasswordHash = !string.IsNullOrWhiteSpace(request.Password)
                ? SharePasswordHasher.Hash(request.Password)
                : null,
            AllowDownload = request.AllowDownload,
            MaxViews = request.MaxViews
        };

        dbContext.SharedLinks.Add(link);
        await dbContext.SaveChangesAsync(ct);

        return Results.Ok(ToResponse(link));
    }

    internal static ShareLinkResponse ToResponse(SharedLink link) => new()
    {
        Token = link.Token,
        AssetId = link.AssetId,
        AlbumId = link.AlbumId,
        CreatedAt = link.CreatedAt,
        ExpiresAt = link.ExpiresAt,
        HasPassword = link.PasswordHash != null,
        AllowDownload = link.AllowDownload,
        MaxViews = link.MaxViews,
        ViewCount = link.ViewCount
    };
}

public class CreateShareRequest
{
    public Guid? AssetId { get; set; }
    public Guid? AlbumId { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public string? Password { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
}

public class ShareLinkResponse
{
    public string Token { get; set; } = string.Empty;
    public Guid? AssetId { get; set; }
    public Guid? AlbumId { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public bool HasPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
    public int ViewCount { get; set; }
}
