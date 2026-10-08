using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Auth;

/// <summary>
/// Manages the <see cref="MediaSessionCookie"/> for web clients. Login and
/// refresh already set it; <c>media-session</c> sets it from the bearer token
/// a client already holds (a session that predates the cookie), and
/// <c>logout</c> drops it so thumbnails stop loading once the user signs out.
/// </summary>
public class MediaSessionEndpoints : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/auth/media-session", (HttpContext httpContext) =>
            {
                var header = httpContext.Request.Headers.Authorization.ToString();
                if (!header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
                    return Results.Unauthorized();

                MediaSessionCookie.Append(httpContext, header["Bearer ".Length..].Trim());
                return Results.NoContent();
            })
            .WithName("CreateMediaSession")
            .WithTags("Authentication")
            .WithDescription("Sets the HttpOnly cookie that lets <img> and <video> load media with the caller's bearer token")
            .RequireAuthorization();

        app.MapPost("/api/auth/logout", (HttpContext httpContext) =>
            {
                MediaSessionCookie.Delete(httpContext);
                return Results.NoContent();
            })
            .WithName("Logout")
            .WithTags("Authentication")
            .WithDescription("Clears the media session cookie")
            .AllowAnonymous();
    }
}
