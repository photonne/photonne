namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

public interface IFaceRecognitionClient
{
    Task<FaceRecognitionResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);
}
