using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Notifications;

public class GetUnreadCountEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/notifications/unread-count", Handle)
            .WithName("GetUnreadNotificationsCount")
            .WithTags("Notifications")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<UnreadCountResponse>, UnauthorizedHttpResult>> Handle(
        [FromServices] INotificationService notificationService,
        HttpContext httpContext,
        CancellationToken ct)
    {
        if (!Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var count = await notificationService.GetUnreadCountAsync(userId);
        return TypedResults.Ok(new UnreadCountResponse(count));
    }
}

public sealed record UnreadCountResponse(int Count);
