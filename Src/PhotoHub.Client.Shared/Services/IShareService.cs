using PhotoHub.Client.Shared.Models;

namespace PhotoHub.Client.Shared.Services;

public interface IShareService
{
    // Public links
    Task<CreateShareLinkResponse?> CreatePublicShareAsync(CreateShareLinkRequest request);
    Task<List<CreateShareLinkResponse>> GetShareLinksAsync(Guid? assetId, Guid? albumId);
    Task RevokeShareAsync(string token);

    // Public page
    Task<SharedContentResponse?> GetSharedContentAsync(string token, string? password = null);

    // Sent links
    Task<List<SentShareLinkDto>> GetSentShareLinksAsync();
}
