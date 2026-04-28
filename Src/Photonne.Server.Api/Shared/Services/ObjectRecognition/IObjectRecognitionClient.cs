namespace Photonne.Server.Api.Shared.Services.ObjectRecognition;

public interface IObjectRecognitionClient
{
    Task<ObjectDetectionResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);
}
