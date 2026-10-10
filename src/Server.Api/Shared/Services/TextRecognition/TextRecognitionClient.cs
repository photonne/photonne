using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Shared.Services.TextRecognition;

public class TextRecognitionClient : ITextRecognitionClient
{
    private const string Capability = "Reconocimiento de texto";

    private readonly HttpClient _http;
    private readonly MlOptions _options;
    private readonly ILogger<TextRecognitionClient> _logger;

    public TextRecognitionClient(HttpClient http, IOptions<MlOptions> options, ILogger<TextRecognitionClient> logger)
    {
        _http = http;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<TextDetectResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default)
    {
        // Normalize separators so the Linux ML service container can read the
        // path even when the API runs on Windows and Path.Combine emitted '\'.
        var req = new TextDetectRequestDto { ImagePath = imagePath.Replace('\\', '/'), AssetId = assetId.ToString() };
        var dto = await MlCall.PostAsync<TextDetectRequestDto, TextDetectResponse>(
            _http, _options, _logger, "/v1/text/detect", req, Capability, $"asset {assetId}", cancellationToken);

        _logger.LogInformation(
            "Text service recognized {Count} lines for asset {AssetId} in {Elapsed} ms",
            dto.Lines.Count, assetId, dto.ElapsedMs);
        return dto;
    }
}
