using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Notifications;

public class MarkAllReadEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPatch("/api/notifications/read-all", Handle)
            .WithName("MarkAllNotificationsRead")
            .WithTags("Notifications")
            .RequireAuthorization();
    }

    private static async Task<Results<NoContent, UnauthorizedHttpResult>> Handle(
        [FromServices] INotificationService notificationService,
        HttpContext httpContext,
        CancellationToken ct)
    {
        if (!Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        await notificationService.MarkAllAsReadAsync(userId);
        return TypedResults.NoContent();
    }
}
