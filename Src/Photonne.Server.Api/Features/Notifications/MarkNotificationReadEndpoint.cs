using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Notifications;

public class MarkNotificationReadEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPatch("/api/notifications/{id:guid}/read", Handle)
            .WithName("MarkNotificationRead")
            .WithTags("Notifications")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] INotificationService notificationService,
        [FromRoute] Guid id,
        HttpContext httpContext,
        CancellationToken ct)
    {
        if (!Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return Results.Unauthorized();

        await notificationService.MarkAsReadAsync(id, userId);
        return Results.NoContent();
    }
}
