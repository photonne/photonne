using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Notifications;

public class GetNotificationsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/notifications", Handle)
            .WithName("GetNotifications")
            .WithTags("Notifications")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] INotificationService notificationService,
        [FromQuery] int page,
        [FromQuery] int pageSize,
        [FromQuery] bool unreadOnly,
        HttpContext httpContext,
        CancellationToken ct)
    {
        if (!Guid.TryParse(httpContext.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return Results.Unauthorized();

        page = page <= 0 ? 1 : page;
        pageSize = pageSize is <= 0 or > 100 ? 20 : pageSize;

        var (items, total) = await notificationService.GetPagedAsync(userId, page, pageSize, unreadOnly);
        var unreadCount = await notificationService.GetUnreadCountAsync(userId);

        return Results.Ok(new
        {
            Items = items.Select(n => new
            {
                n.Id,
                n.Type,
                n.Title,
                n.Message,
                n.IsRead,
                n.CreatedAt,
                n.ActionUrl
            }),
            TotalCount = total,
            Page = page,
            PageSize = pageSize,
            TotalPages = (int)Math.Ceiling((double)total / pageSize),
            UnreadCount = unreadCount
        });
    }
}
