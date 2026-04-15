using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Share;

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
        [FromServices] SettingsService settingsService,
        [FromBody] CreateShareRequest request,
        ClaimsPrincipal user,
        HttpContext httpContext,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        if (request.AlbumId == null)
            return Results.BadRequest(new { error = "AlbumId is required" });

        // Verify ownership / access
        if (request.AlbumId.HasValue)
        {
            var isAdmin = user.IsInRole("Admin");
            var album = await dbContext.Albums
                .Include(a => a.Permissions)
                .FirstOrDefaultAsync(a => a.Id == request.AlbumId, ct);
            if (album == null) return Results.NotFound(new { error = "Album not found" });
            var canShare = isAdmin || album.OwnerId == userId ||
                           album.Permissions.Any(p => p.UserId == userId && p.CanWrite);
            if (!canShare) return Results.Forbid();
        }

        var link = new SharedLink
        {
            Token = Guid.NewGuid().ToString("N"),
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

        var publicBase = await ResolvePublicBaseUrlAsync(settingsService, httpContext);
        return Results.Ok(ToResponse(link, publicBase));
    }

    /// <summary>
    /// Resolves the public base URL for share links. Prefers the admin-configured
    /// <c>ServerSettings.PublicUrl</c>; falls back to the current request's base URL.
    /// </summary>
    internal static async Task<string> ResolvePublicBaseUrlAsync(
        SettingsService settingsService, HttpContext httpContext)
    {
        var configured = await settingsService.GetSettingAsync(
            "ServerSettings.PublicUrl", Guid.Empty, "");

        if (!string.IsNullOrWhiteSpace(configured))
            return configured.TrimEnd('/');

        var request = httpContext.Request;
        return $"{request.Scheme}://{request.Host.Value}".TrimEnd('/');
    }

    internal static ShareLinkResponse ToResponse(SharedLink link, string publicBaseUrl = "") => new()
    {
        Token = link.Token,
        AlbumId = link.AlbumId,
        CreatedAt = link.CreatedAt,
        ExpiresAt = link.ExpiresAt,
        HasPassword = link.PasswordHash != null,
        AllowDownload = link.AllowDownload,
        MaxViews = link.MaxViews,
        ViewCount = link.ViewCount,
        ShareUrl = !string.IsNullOrEmpty(publicBaseUrl)
            ? $"{publicBaseUrl}/share/{link.Token}"
            : $"/share/{link.Token}"
    };
}

public class CreateShareRequest
{
    public Guid? AlbumId { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public string? Password { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
}

public class ShareLinkResponse
{
    public string Token { get; set; } = string.Empty;
    public Guid? AlbumId { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public bool HasPassword { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
    public int ViewCount { get; set; }
    /// <summary>
    /// Absolute URL (based on <c>ServerSettings.PublicUrl</c> when configured, otherwise
    /// the current request's base URL) that end users can open to view the shared content.
    /// </summary>
    public string ShareUrl { get; set; } = string.Empty;
}
