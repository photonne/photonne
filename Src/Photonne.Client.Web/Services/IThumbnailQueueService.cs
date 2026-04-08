using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IThumbnailQueueService
{
    IAsyncEnumerable<ThumbnailProgressUpdate> GenerateThumbnailsAsync(
        bool regenerateAll = false,
        CancellationToken cancellationToken = default);
    IAsyncEnumerable<ThumbnailProgressUpdate> ResumeAsync(Guid taskId, CancellationToken cancellationToken = default);
}
