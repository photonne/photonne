using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IMaintenanceService
{
    Task<MaintenanceTaskResult> CleanOrphanThumbnailsAsync(CancellationToken ct = default);
    Task<MaintenanceTaskResult> MarkMissingFilesAsync(CancellationToken ct = default);
    Task<MaintenanceTaskResult> RecalculateSizesAsync(CancellationToken ct = default);
    Task<MaintenanceTaskResult> EmptyGlobalTrashAsync(CancellationToken ct = default);
}
