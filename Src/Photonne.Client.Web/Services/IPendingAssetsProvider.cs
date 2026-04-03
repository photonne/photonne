using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IPendingAssetsProvider
{
    Task<List<TimelineItem>> GetPendingAssetsAsync();
    Task<AssetDetail?> GetPendingAssetDetailAsync(string path);
}
