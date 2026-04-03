using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Auth;

public class RefreshTokenEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/auth/refresh", Handle)
            .WithName("RefreshToken")
            .WithTags("Authentication")
            .AllowAnonymous()
            .WithDescription("Refreshes JWT using a refresh token");
    }

    private async Task<IResult> Handle(
        [FromBody] RefreshTokenRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.RefreshToken) || string.IsNullOrWhiteSpace(request.DeviceId))
        {
            return Results.BadRequest(new { error = "RefreshToken and DeviceId are required" });
        }

        var deviceId = request.DeviceId.Trim();
        var refreshTokenHash = RefreshTokenHelper.HashToken(request.RefreshToken);
        var tokenEntity = await dbContext.RefreshTokens
            .Include(rt => rt.User)
            .FirstOrDefaultAsync(rt => rt.TokenHash == refreshTokenHash && rt.DeviceId == deviceId, cancellationToken);

        if (tokenEntity == null || tokenEntity.User == null)
        {
            return Results.Unauthorized();
        }

        if (tokenEntity.RevokedAt.HasValue || tokenEntity.ExpiresAt <= DateTime.UtcNow || !tokenEntity.User.IsActive)
        {
            dbContext.RefreshTokens.Remove(tokenEntity);
            await dbContext.SaveChangesAsync(cancellationToken);
            return Results.Unauthorized();
        }

        // Eliminar tokens previos del mismo dispositivo
        var existingTokens = await dbContext.RefreshTokens
            .Where(rt => rt.UserId == tokenEntity.UserId && rt.DeviceId == deviceId)
            .ToListAsync(cancellationToken);
        if (existingTokens.Count > 0)
        {
            dbContext.RefreshTokens.RemoveRange(existingTokens);
        }

        var newRefreshToken = RefreshTokenHelper.GenerateToken();
        var newRefreshTokenHash = RefreshTokenHelper.HashToken(newRefreshToken);
        var refreshEntity = new RefreshToken
        {
            UserId = tokenEntity.UserId,
            DeviceId = deviceId,
            TokenHash = newRefreshTokenHash,
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.AddDays(30)
        };
        dbContext.RefreshTokens.Add(refreshEntity);

        await dbContext.SaveChangesAsync(cancellationToken);

        var token = await authService.GenerateTokenAsync(tokenEntity.User);

        return Results.Ok(new RefreshTokenResponse
        {
            Token = token,
            RefreshToken = newRefreshToken
        });
    }
}

public class RefreshTokenRequest
{
    public string RefreshToken { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
}

public class RefreshTokenResponse
{
    public string Token { get; set; } = string.Empty;
    public string RefreshToken { get; set; } = string.Empty;
}
