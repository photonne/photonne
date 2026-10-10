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
        if (!await IsTypeEnabledAsync(type))
            return;

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

        // Respect per-user cap — trim oldest notifications if over the limit
        await EnforcePerUserCapAsync(userId);
    }

    public async Task CreateOrAggregateAsync(Guid userId, NotificationType type, string groupKey, string title, Func<int, string> message, string? actionUrl = null)
    {
        if (!await IsTypeEnabledAsync(type))
            return;

        // Only the unread row aggregates: once the user reads it, the next
        // event opens a fresh notification (and a fresh count) so nothing new
        // hides behind an already-seen entry.
        var existing = await _db.Notifications
            .Where(n => n.UserId == userId && n.GroupKey == groupKey && !n.IsRead)
            .OrderByDescending(n => n.CreatedAt)
            .FirstOrDefaultAsync();

        if (existing is not null)
        {
            existing.GroupCount++;
            existing.Title = title;
            existing.Message = message(existing.GroupCount);
            existing.CreatedAt = DateTime.UtcNow;
            existing.ActionUrl = actionUrl ?? existing.ActionUrl;
            await _db.SaveChangesAsync();
            return;
        }

        _db.Notifications.Add(new Notification
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Type = type,
            Title = title,
            Message = message(1),
            IsRead = false,
            CreatedAt = DateTime.UtcNow,
            ActionUrl = actionUrl,
            GroupKey = groupKey,
            GroupCount = 1
        });
        await _db.SaveChangesAsync();

        await EnforcePerUserCapAsync(userId);
    }

    /// <summary>Global master switch + per-type toggle from settings.</summary>
    private async Task<bool> IsTypeEnabledAsync(NotificationType type)
    {
        var enabled = await _settings.GetSettingAsync("NotificationSettings.Enabled", Guid.Empty, "true");
        if (!enabled.Equals("true", StringComparison.OrdinalIgnoreCase))
            return false;

        var typeKey = type switch
        {
            NotificationType.JobCompleted => "NotificationSettings.JobCompleted.Enabled",
            NotificationType.JobFailed    => "NotificationSettings.JobFailed.Enabled",
            NotificationType.ShareViewed  => "NotificationSettings.ShareViewed.Enabled",
            NotificationType.SharedAssetsDeleted => "NotificationSettings.SharedAssetsDeleted.Enabled",
            _                             => null
        };
        if (typeKey is null)
            return true;

        var typeEnabled = await _settings.GetSettingAsync(typeKey, Guid.Empty, "true");
        return typeEnabled.Equals("true", StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>
    /// Enforces <c>NotificationSettings.MaxPerUser</c>. When the user has more
    /// notifications than the configured cap, the oldest ones are deleted.
    /// A value of 0 (or any non-positive / unparseable value) disables the cap.
    /// </summary>
    private async Task EnforcePerUserCapAsync(Guid userId)
    {
        var raw = await _settings.GetSettingAsync("NotificationSettings.MaxPerUser", Guid.Empty, "0");
        if (!int.TryParse(raw, out var maxPerUser) || maxPerUser <= 0)
            return;

        var total = await _db.Notifications.CountAsync(n => n.UserId == userId);
        if (total <= maxPerUser)
            return;

        var excess = total - maxPerUser;
        var toDelete = await _db.Notifications
            .Where(n => n.UserId == userId)
            .OrderBy(n => n.CreatedAt)
            .Take(excess)
            .Select(n => n.Id)
            .ToListAsync();

        if (toDelete.Count == 0)
            return;

        await _db.Notifications
            .Where(n => toDelete.Contains(n.Id))
            .ExecuteDeleteAsync();
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
