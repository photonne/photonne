namespace Photonne.Client.Web.Models;

public enum NotificationTypeDto
{
    JobCompleted = 1,
    JobFailed = 2,
    ShareViewed = 3
}

public class NotificationDto
{
    public Guid Id { get; set; }
    public NotificationTypeDto Type { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public bool IsRead { get; set; }
    public DateTime CreatedAt { get; set; }
    public string? ActionUrl { get; set; }
}

public class NotificationsPageResponse
{
    public List<NotificationDto> Items { get; set; } = [];
    public int TotalCount { get; set; }
    public int Page { get; set; }
    public int PageSize { get; set; }
    public int TotalPages { get; set; }
    public int UnreadCount { get; set; }
}
