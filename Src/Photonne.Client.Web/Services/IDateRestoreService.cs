using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IDateRestoreService
{
    IAsyncEnumerable<MetadataProgressUpdate> RestoreDatesAsync(
        bool fromFile = false,
        CancellationToken cancellationToken = default);
    IAsyncEnumerable<MetadataProgressUpdate> ResumeAsync(Guid taskId, CancellationToken cancellationToken = default);
}
