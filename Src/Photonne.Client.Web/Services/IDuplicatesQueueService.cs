using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

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
