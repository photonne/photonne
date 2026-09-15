using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Shared.Services.Embeddings;

public class EmbeddingClient : IEmbeddingClient
{
    private const string Capability = "Embeddings de imagen";

    private readonly HttpClient _http;
    private readonly MlOptions _options;
    private readonly ILogger<EmbeddingClient> _logger;

    public EmbeddingClient(HttpClient http, IOptions<MlOptions> options, ILogger<EmbeddingClient> logger)
    {
        _http = http;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<EmbeddingResponseDto> EmbedImageAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default)
    {
        // Normalize separators so the Linux ML service container can read the
        // path even when the API runs on Windows and Path.Combine emitted '\'.
        var req = new EmbedImageRequestDto { ImagePath = imagePath.Replace('\\', '/'), AssetId = assetId.ToString() };
        var dto = await MlCall.PostAsync<EmbedImageRequestDto, EmbeddingResponseDto>(
            _http, _options, _logger, "/v1/embeddings/image", req, Capability, $"asset {assetId}", cancellationToken);

        _logger.LogInformation(
            "Embedding service produced dim={Dim} for asset {AssetId} in {Elapsed} ms",
            dto.Dim, assetId, dto.ElapsedMs);
        return dto;
    }

    public async Task<EmbeddingResponseDto> EmbedTextAsync(string text, CancellationToken cancellationToken = default)
    {
        var req = new EmbedTextRequestDto { Text = text };
        // Truncate the log key — full search strings can be long and we
        // don't want them inside structured log fields.
        var logKey = text.Length > 64 ? text[..64] + "…" : text;
        var dto = await MlCall.PostAsync<EmbedTextRequestDto, EmbeddingResponseDto>(
            _http, _options, _logger, "/v1/embeddings/text", req, "Embeddings de texto", $"texto \"{logKey}\"", cancellationToken);

        _logger.LogInformation(
            "Embedding service produced dim={Dim} for {Target} in {Elapsed} ms",
            dto.Dim, logKey, dto.ElapsedMs);
        return dto;
    }
}
