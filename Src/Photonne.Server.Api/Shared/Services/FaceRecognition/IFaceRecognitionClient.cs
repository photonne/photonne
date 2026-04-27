namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

public interface IFaceRecognitionClient
{
    Task<FaceDetectionResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);
}
