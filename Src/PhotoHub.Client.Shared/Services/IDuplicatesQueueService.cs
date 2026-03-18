using PhotoHub.Client.Shared.Models;

namespace PhotoHub.Client.Shared.Services;

public interface IDuplicatesQueueService
{
    IAsyncEnumerable<DuplicatesProgressUpdate> DetectDuplicatesAsync(
        bool cleanup = false,
        bool physical = false,
        CancellationToken cancellationToken = default);

    Task<PhysicalDeleteResult> DeletePhysicalFilesAsync(
        List<PhysicalFileDeleteRequest> files,
        CancellationToken cancellationToken = default);
}
