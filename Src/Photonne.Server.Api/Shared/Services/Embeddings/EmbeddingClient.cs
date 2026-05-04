using System.Net.Http.Json;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Shared.Services.Embeddings;

public class EmbeddingClient : IEmbeddingClient
{
    private readonly HttpClient _http;
    private readonly MlOptions _options;
    private readonly ILogger<EmbeddingClient> _logger;

    public EmbeddingClient(HttpClient http, IOptions<MlOptions> options, ILogger<EmbeddingClient> logger)
    {
        _http = http;
        _options = options.Value;
        _logger = logger;
    }

    public Task<EmbeddingResponseDto> EmbedImageAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default)
    {
        // Normalize separators so the Linux ML service container can read the
        // path even when the API runs on Windows and Path.Combine emitted '\'.
        var req = new EmbedImageRequestDto { ImagePath = imagePath.Replace('\\', '/'), AssetId = assetId.ToString() };
        return PostWithRetryAsync("/v1/embeddings/image", req, $"asset {assetId}", cancellationToken);
    }

    public Task<EmbeddingResponseDto> EmbedTextAsync(string text, CancellationToken cancellationToken = default)
    {
        var req = new EmbedTextRequestDto { Text = text };
        // Truncate the log key — full search strings can be long and we
        // don't want them inside structured log fields.
        var logKey = text.Length > 64 ? text[..64] + "…" : text;
        return PostWithRetryAsync("/v1/embeddings/text", req, $"text \"{logKey}\"", cancellationToken);
    }

    private async Task<EmbeddingResponseDto> PostWithRetryAsync<T>(
        string path, T body, string logTarget, CancellationToken cancellationToken)
    {
        var attempts = Math.Max(1, _options.MaxRetries + 1);
        Exception? lastException = null;

        for (var attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                using var response = await _http.PostAsJsonAsync(path, body, cancellationToken);

                if (!response.IsSuccessStatusCode)
                {
                    var errBody = await response.Content.ReadAsStringAsync(cancellationToken);
                    var status = (int)response.StatusCode;
                    if (status is >= 400 and < 500 && status is not 408 and not 429)
                    {
                        throw new HttpRequestException(
                            $"Embedding service returned {status} for {logTarget}: {errBody}");
                    }
                    throw new HttpRequestException(
                        $"Embedding service returned transient {status} for {logTarget}: {errBody}");
                }

                var dto = await response.Content.ReadFromJsonAsync<EmbeddingResponseDto>(cancellationToken: cancellationToken)
                    ?? throw new InvalidOperationException($"Empty response from embedding service for {logTarget}");

                _logger.LogInformation(
                    "Embedding service produced dim={Dim} for {Target} in {Elapsed} ms (attempt {Attempt})",
                    dto.Dim, logTarget, dto.ElapsedMs, attempt);

                return dto;
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or InvalidOperationException)
            {
                lastException = ex;
                if (attempt >= attempts) break;
                var delay = TimeSpan.FromSeconds(Math.Pow(2, attempt));
                _logger.LogWarning(ex,
                    "Embedding service call failed for {Target} (attempt {Attempt}/{Total}); retrying in {Delay}s",
                    logTarget, attempt, attempts, delay.TotalSeconds);
                await Task.Delay(delay, cancellationToken);
            }
        }

        throw new InvalidOperationException(
            $"Embedding service call failed after {attempts} attempts for {logTarget}", lastException);
    }
}
