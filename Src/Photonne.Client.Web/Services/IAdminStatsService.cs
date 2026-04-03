using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IAdminStatsService
{
    Task<AdminStatsResponse> GetStatsAsync();
}
