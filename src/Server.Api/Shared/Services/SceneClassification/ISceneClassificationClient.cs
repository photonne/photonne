namespace Photonne.Server.Api.Shared.Services.SceneClassification;

public interface ISceneClassificationClient
{
    Task<SceneClassificationResponse> ClassifyAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);
}
