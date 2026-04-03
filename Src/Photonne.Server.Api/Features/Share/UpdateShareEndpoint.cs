using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Share;

public class UpdateShareEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPatch("/api/share/{token}", Handle)
            .WithName("UpdateShareLink")
            .WithTags("Share")
            .WithDescription("Updates expiration, password, download permission or max views of a share link")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] string token,
        [FromBody] UpdateShareRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();

        var link = await dbContext.SharedLinks.FirstOrDefaultAsync(l => l.Token == token, ct);
        if (link == null) return Results.NotFound();

        var isAdmin = user.IsInRole("Admin");
        if (!isAdmin && link.CreatedById != userId) return Results.Forbid();

        link.ExpiresAt = request.ExpiresAt;
        link.AllowDownload = request.AllowDownload;
        link.MaxViews = request.MaxViews;

        // null  → keep existing password unchanged
        // ""    → remove password
        // value → set new password
        if (request.Password is not null)
        {
            link.PasswordHash = request.Password.Length > 0
                ? SharePasswordHasher.Hash(request.Password)
                : null;
        }

        await dbContext.SaveChangesAsync(ct);

        return Results.Ok(new UpdateShareResponse
        {
            Token = link.Token,
            ExpiresAt = link.ExpiresAt,
            HasPassword = link.PasswordHash != null,
            AllowDownload = link.AllowDownload,
            MaxViews = link.MaxViews
        });
    }
}

public class UpdateShareRequest
{
    public DateTime? ExpiresAt { get; set; }
    /// <summary>null = keep current | "" = remove | value = set new password</summary>
    public string? Password { get; set; }
    public bool AllowDownload { get; set; } = true;
    public int? MaxViews { get; set; }
}

public class UpdateShareResponse
{
    public string Token { get; set; } = string.Empty;
    public DateTime? ExpiresAt { get; set; }
    public bool HasPassword { get; set; }
    public bool AllowDownload { get; set; }
    public int? MaxViews { get; set; }
}
