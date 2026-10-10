using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Shared.Services.SceneClassification;

public class SceneClassificationClient : ISceneClassificationClient
{
    private const string Capability = "Clasificación de escenas";

    private readonly HttpClient _http;
    private readonly MlOptions _options;
    private readonly ILogger<SceneClassificationClient> _logger;

    public SceneClassificationClient(HttpClient http, IOptions<MlOptions> options, ILogger<SceneClassificationClient> logger)
    {
        _http = http;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<SceneClassificationResponse> ClassifyAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default)
    {
        // Normalize separators so the Linux ML service container can read the
        // path even when the API runs on Windows and Path.Combine emitted '\'.
        var req = new SceneClassifyRequestDto { ImagePath = imagePath.Replace('\\', '/'), AssetId = assetId.ToString() };
        var dto = await MlCall.PostAsync<SceneClassifyRequestDto, SceneClassificationResponse>(
            _http, _options, _logger, "/v1/scenes/classify", req, Capability, $"asset {assetId}", cancellationToken);

        _logger.LogInformation(
            "Scene service classified {Count} scenes for asset {AssetId} in {Elapsed} ms",
            dto.Scenes.Count, assetId, dto.ElapsedMs);
        return dto;
    }
}
