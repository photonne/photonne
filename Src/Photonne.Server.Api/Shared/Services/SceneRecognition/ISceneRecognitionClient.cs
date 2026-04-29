namespace Photonne.Server.Api.Shared.Services.SceneRecognition;

public interface ISceneRecognitionClient
{
    Task<SceneClassificationResponse> ClassifyAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);
}
