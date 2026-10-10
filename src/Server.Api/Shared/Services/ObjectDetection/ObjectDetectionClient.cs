using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Shared.Services.ObjectDetection;

public class ObjectDetectionClient : IObjectDetectionClient
{
    private const string Capability = "Detección de objetos";

    private readonly HttpClient _http;
    private readonly MlOptions _options;
    private readonly ILogger<ObjectDetectionClient> _logger;

    public ObjectDetectionClient(HttpClient http, IOptions<MlOptions> options, ILogger<ObjectDetectionClient> logger)
    {
        _http = http;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<ObjectDetectionResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default)
    {
        // Normalize separators so the Linux ML service container can read the
        // path even when the API runs on Windows and Path.Combine emitted '\'.
        var req = new ObjectDetectRequestDto { ImagePath = imagePath.Replace('\\', '/'), AssetId = assetId.ToString() };
        var dto = await MlCall.PostAsync<ObjectDetectRequestDto, ObjectDetectionResponse>(
            _http, _options, _logger, "/v1/objects/detect", req, Capability, $"asset {assetId}", cancellationToken);

        _logger.LogInformation(
            "Object service detected {Count} objects for asset {AssetId} in {Elapsed} ms",
            dto.Objects.Count, assetId, dto.ElapsedMs);
        return dto;
    }
}
