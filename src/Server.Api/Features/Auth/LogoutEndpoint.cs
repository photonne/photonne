using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Auth;

/// <summary>
/// Ends a session: revokes the refresh token held in <see cref="RefreshTokenCookie"/>
/// (web) or sent in the body (native apps, which keep it themselves), and deletes
/// both session cookies so media stops loading. Anonymous on purpose — an expired
/// access token must not stop a user from signing out. Without the body, a native
/// sign-out only forgot the token on the device and it stayed valid for 30 days.
/// </summary>
public class LogoutEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/auth/logout", Handle)
            .WithName("Logout")
            .WithTags("Authentication")
            .WithDescription("Revokes the refresh token cookie and clears the session cookies")
            .AllowAnonymous();
    }

    private static async Task<NoContent> Handle(
        [FromServices] ApplicationDbContext dbContext,
        HttpContext httpContext,
        CancellationToken cancellationToken)
    {
        // The body is read by hand: a [FromBody] parameter makes routing skip this
        // endpoint for the web's bodyless POST, which then fell into the /api 404.
        var request = httpContext.Request.HasJsonContentType()
            ? await httpContext.Request.ReadFromJsonAsync<LogoutRequest>(cancellationToken)
            : null;
        var refreshToken = RefreshTokenCookie.Read(httpContext)
            ?? (string.IsNullOrWhiteSpace(request?.RefreshToken) ? null : request.RefreshToken.Trim());
        if (refreshToken is not null)
        {
            var hash = RefreshTokenHelper.HashToken(refreshToken);
            await dbContext.RefreshTokens
                .Where(rt => rt.TokenHash == hash)
                .ExecuteDeleteAsync(cancellationToken);
        }

        RefreshTokenCookie.Delete(httpContext);
        MediaSessionCookie.Delete(httpContext);
        return TypedResults.NoContent();
    }
}

public sealed record LogoutRequest(string? RefreshToken);
