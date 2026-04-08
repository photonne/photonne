using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IMetadataQueueService
{
    IAsyncEnumerable<MetadataProgressUpdate> ExtractMetadataAsync(
        bool overwriteAll = false,
        CancellationToken cancellationToken = default);
    IAsyncEnumerable<MetadataProgressUpdate> ResumeAsync(Guid taskId, CancellationToken cancellationToken = default);
}
