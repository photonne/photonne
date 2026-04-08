using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface INotificationService
{
    Task<NotificationsPageResponse> GetNotificationsAsync(int page = 1, int pageSize = 20, bool unreadOnly = false);
    Task<int> GetUnreadCountAsync();
    Task MarkAsReadAsync(Guid id);
    Task MarkAllAsReadAsync();
}
