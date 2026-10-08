using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Auth;

/// <summary>
/// Ends a web session: revokes the refresh token held in
/// <see cref="RefreshTokenCookie"/>, if any, and deletes both session cookies so
/// media stops loading. Anonymous on purpose — an expired access token must
/// not stop a user from signing out. Native clients just forget their tokens.
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
        if (RefreshTokenCookie.Read(httpContext) is { } refreshToken)
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
