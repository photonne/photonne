using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IExternalLibraryService
{
    Task<List<ExternalLibraryDto>> GetAllAsync(CancellationToken ct = default);
    Task<ExternalLibraryDto?> GetByIdAsync(Guid id, CancellationToken ct = default);
    Task<ExternalLibraryDto?> CreateAsync(CreateExternalLibraryRequest request, CancellationToken ct = default);
    Task<bool> UpdateAsync(Guid id, UpdateExternalLibraryRequest request, CancellationToken ct = default);
    Task<bool> DeleteAsync(Guid id, CancellationToken ct = default);
    IAsyncEnumerable<LibraryScanProgressUpdate> ScanAsync(Guid id, CancellationToken ct = default);
}
