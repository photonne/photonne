using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Auth;

/// <summary>
/// Opens a session for a user already proven to be who they say (password,
/// or an OpenID provider): the access token, a refresh token for this device
/// (replacing the device's previous one) and the media cookie.
/// </summary>
public static class SessionIssuer
{
    public static async Task<LoginResponse> IssueAsync(
        User user,
        string deviceId,
        bool refreshTokenInCookie,
        ApplicationDbContext dbContext,
        IAuthService authService,
        HttpContext httpContext,
        CancellationToken cancellationToken)
    {
        user.LastLoginAt = DateTime.UtcNow;

        var existingTokens = await dbContext.RefreshTokens
            .Where(rt => rt.UserId == user.Id && rt.DeviceId == deviceId)
            .ToListAsync(cancellationToken);
        if (existingTokens.Count > 0)
        {
            dbContext.RefreshTokens.RemoveRange(existingTokens);
        }

        var refreshToken = RefreshTokenHelper.GenerateToken();
        var refreshEntity = new RefreshToken
        {
            UserId = user.Id,
            DeviceId = deviceId,
            TokenHash = RefreshTokenHelper.HashToken(refreshToken),
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.AddDays(30)
        };
        dbContext.RefreshTokens.Add(refreshEntity);

        await dbContext.SaveChangesAsync(cancellationToken);

        var token = await authService.GenerateTokenAsync(user);
        MediaSessionCookie.Append(httpContext, token);
        if (refreshTokenInCookie)
        {
            RefreshTokenCookie.Append(httpContext, refreshToken, refreshEntity.ExpiresAt);
        }

        return new LoginResponse
        {
            Token = token,
            RefreshToken = refreshTokenInCookie ? string.Empty : refreshToken,
            User = new UserDto
            {
                Id = user.Id,
                Username = user.Username,
                Email = user.Email,
                Role = user.Role,
                FirstName = user.FirstName,
                LastName = user.LastName,
                IsActive = user.IsActive,
                IsPrimaryAdmin = user.IsPrimaryAdmin,
                CreatedAt = user.CreatedAt,
                LastLoginAt = user.LastLoginAt,
                StorageQuotaBytes = user.StorageQuotaBytes
            }
        };
    }
}
