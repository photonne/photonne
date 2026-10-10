using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

public class FaceRecognitionClient : IFaceRecognitionClient
{
    private const string Capability = "Reconocimiento facial";

    private readonly HttpClient _http;
    private readonly MlOptions _options;
    private readonly ILogger<FaceRecognitionClient> _logger;

    public FaceRecognitionClient(HttpClient http, IOptions<MlOptions> options, ILogger<FaceRecognitionClient> logger)
    {
        _http = http;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<FaceRecognitionResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default)
    {
        // Normalize separators so the Linux ML service container can read the
        // path even when the API runs on Windows and Path.Combine emitted '\'.
        var req = new DetectRequestDto { ImagePath = imagePath.Replace('\\', '/'), AssetId = assetId.ToString() };
        var dto = await MlCall.PostAsync<DetectRequestDto, FaceRecognitionResponse>(
            _http, _options, _logger, "/v1/faces/detect", req, Capability, $"asset {assetId}", cancellationToken);

        _logger.LogInformation(
            "Face service detected {Count} faces for asset {AssetId} in {Elapsed} ms",
            dto.Faces.Count, assetId, dto.ElapsedMs);
        return dto;
    }
}
