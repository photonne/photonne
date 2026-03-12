using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Models;
using PhotoHub.Server.Api.Shared.Services;
using Scalar.AspNetCore;

namespace PhotoHub.Server.Api.Features.Auth;

public class LoginEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/auth/login", Handle)
            .WithName("Login")
            .WithTags("Authentication")
            .AllowAnonymous()
            .WithDescription("Authenticates a user and returns a JWT token");
    }

    private async Task<IResult> Handle(
        [FromBody] LoginRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Username) || string.IsNullOrWhiteSpace(request.Password))
        {
            return Results.BadRequest(new { error = "Username and password are required" });
        }

        if (string.IsNullOrWhiteSpace(request.DeviceId))
        {
            return Results.BadRequest(new { error = "DeviceId is required" });
        }
        var deviceId = request.DeviceId.Trim();

        var user = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Username == request.Username || u.Email == request.Username, cancellationToken);

        if (user == null || !user.IsActive)
        {
            return Results.Unauthorized();
        }

        if (!authService.VerifyPassword(request.Password, user.PasswordHash))
        {
            return Results.Unauthorized();
        }

        // Actualizar último login
        user.LastLoginAt = DateTime.UtcNow;

        // Eliminar tokens previos del mismo dispositivo
        var existingTokens = await dbContext.RefreshTokens
            .Where(rt => rt.UserId == user.Id && rt.DeviceId == deviceId)
            .ToListAsync(cancellationToken);
        if (existingTokens.Count > 0)
        {
            dbContext.RefreshTokens.RemoveRange(existingTokens);
        }

        var refreshToken = RefreshTokenHelper.GenerateToken();
        var refreshTokenHash = RefreshTokenHelper.HashToken(refreshToken);
        var refreshEntity = new RefreshToken
        {
            UserId = user.Id,
            DeviceId = deviceId,
            TokenHash = refreshTokenHash,
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.AddDays(30)
        };
        dbContext.RefreshTokens.Add(refreshEntity);

        await dbContext.SaveChangesAsync(cancellationToken);

        var token = await authService.GenerateTokenAsync(user);

        return Results.Ok(new LoginResponse
        {
            Token = token,
            RefreshToken = refreshToken,
            User = new UserDto
            {
                Id = user.Id,
                Username = user.Username,
                Email = user.Email,
                Role = user.Role,
                FirstName = user.FirstName,
                LastName = user.LastName
            }
        });
    }
}

public class LoginRequest
{
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
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
    public DateTime CreatedAt { get; set; }
    public DateTime? LastLoginAt { get; set; }
    public long? StorageQuotaBytes { get; set; }
}
