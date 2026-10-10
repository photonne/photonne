namespace Photonne.Server.Api.Shared.Models;

public enum NotificationType
{
    JobCompleted = 1,
    JobFailed = 2,
    ShareViewed = 3,
    SharedAssetsDeleted = 4,
    ShareUploaded = 5
}

public class Notification
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;
    public NotificationType Type { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public bool IsRead { get; set; }
    public DateTime CreatedAt { get; set; }
    public string? ActionUrl { get; set; }

    /// <summary>
    /// Aggregation key: while a notification with the same (UserId, GroupKey)
    /// remains unread, new events with that key fold into it (bumping
    /// <see cref="GroupCount"/> and <see cref="CreatedAt"/>) instead of
    /// inserting a new row. Null = never aggregates.
    /// </summary>
    public string? GroupKey { get; set; }
    public int GroupCount { get; set; } = 1;
}
