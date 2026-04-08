using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class NotificationService : INotificationService
{
    private readonly ApplicationDbContext _db;
    private readonly SettingsService _settings;

    public NotificationService(ApplicationDbContext db, SettingsService settings)
    {
        _db = db;
        _settings = settings;
    }

    public async Task CreateAsync(Guid userId, NotificationType type, string title, string message, string? actionUrl = null)
    {
        // Respect global master switch
        var enabled = await _settings.GetSettingAsync("NotificationSettings.Enabled", Guid.Empty, "true");
        if (!enabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            return;

        // Respect per-type toggle
        var typeKey = type switch
        {
            NotificationType.JobCompleted => "NotificationSettings.JobCompleted.Enabled",
            NotificationType.JobFailed    => "NotificationSettings.JobFailed.Enabled",
            NotificationType.ShareViewed  => "NotificationSettings.ShareViewed.Enabled",
            _                             => null
        };
        if (typeKey is not null)
        {
            var typeEnabled = await _settings.GetSettingAsync(typeKey, Guid.Empty, "true");
            if (!typeEnabled.Equals("true", StringComparison.OrdinalIgnoreCase))
                return;
        }

        _db.Notifications.Add(new Notification
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Type = type,
            Title = title,
            Message = message,
            IsRead = false,
            CreatedAt = DateTime.UtcNow,
            ActionUrl = actionUrl
        });
        await _db.SaveChangesAsync();
    }

    public async Task<(List<Notification> Items, int TotalCount)> GetPagedAsync(Guid userId, int page, int pageSize, bool unreadOnly)
    {
        var query = _db.Notifications.Where(n => n.UserId == userId);
        if (unreadOnly) query = query.Where(n => !n.IsRead);

        var total = await query.CountAsync();
        var items = await query
            .OrderByDescending(n => n.CreatedAt)
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync();

        return (items, total);
    }

    public async Task<int> GetUnreadCountAsync(Guid userId)
        => await _db.Notifications.CountAsync(n => n.UserId == userId && !n.IsRead);

    public async Task MarkAsReadAsync(Guid notificationId, Guid userId)
    {
        var notification = await _db.Notifications
            .FirstOrDefaultAsync(n => n.Id == notificationId && n.UserId == userId);
        if (notification is null) return;
        notification.IsRead = true;
        await _db.SaveChangesAsync();
    }

    public async Task MarkAllAsReadAsync(Guid userId)
        => await _db.Notifications
            .Where(n => n.UserId == userId && !n.IsRead)
            .ExecuteUpdateAsync(s => s.SetProperty(n => n.IsRead, true));
}
