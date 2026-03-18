using PhotoHub.Client.Shared.Models;

namespace PhotoHub.Client.Shared.Services;

public interface IThumbnailQueueService
{
    IAsyncEnumerable<ThumbnailProgressUpdate> GenerateThumbnailsAsync(
        bool regenerateAll = false,
        CancellationToken cancellationToken = default);
}
