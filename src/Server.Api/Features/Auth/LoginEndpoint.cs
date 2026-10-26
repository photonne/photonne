using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Auth;

namespace Photonne.Server.Api.Features.Auth;

public class LoginEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var login = app.MapPost("/api/auth/login", Handle)
            .WithName("Login")
            .WithTags("Authentication")
            .AllowAnonymous()
            .WithDescription("Authenticates a user and returns a JWT token");

        // Rate limiter policy only exists when demo mode is on; guard with an
        // attribute metadata so the endpoint still maps correctly in prod.
        login.RequireRateLimiting("demo-login");
    }

    private async Task<Results<Ok<LoginResponse>, BadRequest<ApiError>, UnauthorizedHttpResult, JsonHttpResult<ApiError>>> Handle(
        [FromBody] LoginRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        HttpContext httpContext,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Username) || string.IsNullOrWhiteSpace(request.Password))
        {
            return TypedResults.BadRequest(new ApiError("Username and password are required", "credentials_required"));
        }

        if (string.IsNullOrWhiteSpace(request.DeviceId))
        {
            return TypedResults.BadRequest(new ApiError("DeviceId is required", "device_id_required"));
        }
        var deviceId = request.DeviceId.Trim();

        var usernameNormalized = request.Username.Trim().ToLowerInvariant();
        var user = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Username.ToLower() == usernameNormalized || u.Email.ToLower() == usernameNormalized, cancellationToken);

        if (user == null || !user.IsActive)
        {
            return TypedResults.Unauthorized();
        }

        if (!authService.VerifyPassword(request.Password, user.PasswordHash))
        {
            return TypedResults.Unauthorized();
        }

        // Two-step verification: the password was right, now the code (or a
        // recovery code). Told apart from bad credentials so the client asks
        // for the code instead of saying the password is wrong.
        if (user.TotpEnabled && !string.IsNullOrEmpty(user.TotpSecret))
        {
            if (string.IsNullOrWhiteSpace(request.TotpCode))
                return TypedResults.Json(new ApiError("Two-step verification code required", "totp_required"),
                    statusCode: StatusCodes.Status401Unauthorized);

            var step = Totp.Verify(user.TotpSecret, request.TotpCode, DateTimeOffset.UtcNow, user.TotpLastStep);
            if (step is not null)
            {
                user.TotpLastStep = step;
            }
            else if (RecoveryCodes.Consume(user.TotpRecoveryCodes, request.TotpCode) is { } left)
            {
                user.TotpRecoveryCodes = left;
            }
            else
            {
                return TypedResults.Json(new ApiError("Two-step verification code is not right", "totp_invalid"),
                    statusCode: StatusCodes.Status401Unauthorized);
            }
        }

        // Hashes from before PBKDF2 are upgraded while we hold the password.
        if (authService.NeedsRehash(user.PasswordHash))
        {
            user.PasswordHash = authService.HashPassword(request.Password);
        }

        return TypedResults.Ok(await SessionIssuer.IssueAsync(
            user, deviceId, request.RefreshTokenInCookie, dbContext, authService, httpContext, cancellationToken));
    }
}

public class LoginRequest
{
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;

    /// <summary>
    /// Web clients: keep the refresh token in an HttpOnly cookie instead of the
    /// response body (see <see cref="RefreshTokenCookie"/>).
    /// </summary>
    public bool RefreshTokenInCookie { get; set; }

    /// <summary>
    /// The authenticator code (or a recovery code) of an account with
    /// two-step verification; without it such a login answers 401
    /// <c>totp_required</c>.
    /// </summary>
    public string? TotpCode { get; set; }
}

public class LoginResponse
{
    public string Token { get; set; } = string.Empty;
    public string RefreshToken { get; set; } = string.Empty;
    public UserDto User { get; set; } = null!;
}

public class UserDto
{
    public Guid Id { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string Role { get; set; } = string.Empty;
    public string? FirstName { get; set; }
    public string? LastName { get; set; }
    public bool IsActive { get; set; }
    public bool IsPrimaryAdmin { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? LastLoginAt { get; set; }
    public long? StorageQuotaBytes { get; set; }
}
