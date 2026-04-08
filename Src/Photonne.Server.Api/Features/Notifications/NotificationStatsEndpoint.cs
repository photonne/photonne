using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Notifications;

public class NotificationStatsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/notifications")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("stats", GetStats)
            .WithName("GetNotificationStats")
            .WithDescription("Returns global notification counts and oldest entry date");

        group.MapPost("purge", Purge)
            .WithName("PurgeNotifications")
            .WithDescription("Deletes notifications older than the configured retention period (or all read ones if retention = 0)");
    }

    private static async Task<IResult> GetStats(
        [FromServices] ApplicationDbContext db,
        CancellationToken ct)
    {
        var total  = await db.Notifications.CountAsync(ct);
        var unread = await db.Notifications.CountAsync(n => !n.IsRead, ct);
        var oldest = total > 0
            ? await db.Notifications.MinAsync(n => (DateTime?)n.CreatedAt, ct)
            : null;

        return Results.Ok(new NotificationStatsResponse(total, unread, oldest));
    }

    private static async Task<IResult> Purge(
        [FromServices] ApplicationDbContext db,
        [FromServices] SettingsService settings,
        CancellationToken ct)
    {
        var retentionStr = await settings.GetSettingAsync("NotificationSettings.RetentionDays", Guid.Empty, "30");
        int.TryParse(retentionStr, out var retentionDays);

        int deleted;
        if (retentionDays > 0)
        {
            var cutoff = DateTime.UtcNow.AddDays(-retentionDays);
            deleted = await db.Notifications
                .Where(n => n.CreatedAt < cutoff)
                .ExecuteDeleteAsync(ct);
        }
        else
        {
            // retention = 0 means "no auto-delete", manual purge deletes only read notifications
            deleted = await db.Notifications
                .Where(n => n.IsRead)
                .ExecuteDeleteAsync(ct);
        }

        return Results.Ok(new { Deleted = deleted });
    }
}

public sealed record NotificationStatsResponse(int Total, int Unread, DateTime? OldestAt);
