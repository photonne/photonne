namespace Photonne.Server.Api.Shared.Services.TextRecognition;

public interface ITextRecognitionClient
{
    Task<TextDetectResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);
}
