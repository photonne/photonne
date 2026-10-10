using Microsoft.AspNetCore.Http.HttpResults;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Auth;

/// <summary>
/// Sets the <see cref="MediaSessionCookie"/> from the bearer token a web
/// client already holds — a session that predates the cookie. Login and
/// refresh set it on their own; <see cref="LogoutEndpoint"/> drops it.
/// </summary>
public class MediaSessionEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/auth/media-session", Results<NoContent, UnauthorizedHttpResult> (HttpContext httpContext) =>
            {
                var header = httpContext.Request.Headers.Authorization.ToString();
                if (!header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
                    return TypedResults.Unauthorized();

                MediaSessionCookie.Append(httpContext, header["Bearer ".Length..].Trim());
                return TypedResults.NoContent();
            })
            .WithName("CreateMediaSession")
            .WithTags("Authentication")
            .WithDescription("Sets the HttpOnly cookie that lets <img> and <video> load media with the caller's bearer token")
            .RequireAuthorization();
    }
}
