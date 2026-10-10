using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public interface INotificationService
{
    Task CreateAsync(Guid userId, NotificationType type, string title, string message, string? actionUrl = null);

    /// <summary>
    /// Like <see cref="CreateAsync"/>, but folds repeated events into a single
    /// unread notification per (user, groupKey): the existing row's counter and
    /// timestamp are bumped and its message rewritten via <paramref name="message"/>,
    /// which receives the accumulated event count (1 on first creation).
    /// </summary>
    Task CreateOrAggregateAsync(Guid userId, NotificationType type, string groupKey, string title, Func<int, string> message, string? actionUrl = null);
    Task<(List<Notification> Items, int TotalCount)> GetPagedAsync(Guid userId, int page, int pageSize, bool unreadOnly);
    Task<int> GetUnreadCountAsync(Guid userId);
    Task MarkAsReadAsync(Guid notificationId, Guid userId);
    Task MarkAllAsReadAsync(Guid userId);
}
