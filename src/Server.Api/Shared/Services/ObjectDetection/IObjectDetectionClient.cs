namespace Photonne.Server.Api.Shared.Services.ObjectDetection;

public interface IObjectDetectionClient
{
    Task<ObjectDetectionResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);
}
