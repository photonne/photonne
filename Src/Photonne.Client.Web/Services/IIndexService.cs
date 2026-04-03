using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IIndexService
{
    IAsyncEnumerable<IndexProgressUpdate> IndexDirectoryAsync(CancellationToken cancellationToken = default);
}
