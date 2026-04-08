using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IIndexService
{
    IAsyncEnumerable<IndexProgressUpdate> IndexDirectoryAsync(CancellationToken cancellationToken = default);
    IAsyncEnumerable<IndexProgressUpdate> ResumeAsync(Guid taskId, CancellationToken cancellationToken = default);
}
