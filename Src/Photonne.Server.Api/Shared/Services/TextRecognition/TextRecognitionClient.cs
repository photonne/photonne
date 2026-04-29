using System.Net.Http.Json;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Shared.Services.TextRecognition;

public class TextRecognitionClient : ITextRecognitionClient
{
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

        var attempts = Math.Max(1, _options.MaxRetries + 1);
        Exception? lastException = null;

        for (var attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                using var response = await _http.PostAsJsonAsync("/v1/text/detect", req, cancellationToken);

                if (!response.IsSuccessStatusCode)
                {
                    var body = await response.Content.ReadAsStringAsync(cancellationToken);
                    var status = (int)response.StatusCode;
                    // 4xx (except 408/429) are not transient — fail fast.
                    if (status is >= 400 and < 500 && status is not 408 and not 429)
                    {
                        throw new HttpRequestException(
                            $"Text service returned {status} for asset {assetId}: {body}");
                    }
                    throw new HttpRequestException(
                        $"Text service returned transient {status} for asset {assetId}: {body}");
                }

                var dto = await response.Content.ReadFromJsonAsync<TextDetectResponse>(cancellationToken: cancellationToken)
                    ?? throw new InvalidOperationException($"Empty response from text service for asset {assetId}");

                _logger.LogInformation(
                    "Text service recognized {Count} lines for asset {AssetId} in {Elapsed} ms (attempt {Attempt})",
                    dto.Lines.Count, assetId, dto.ElapsedMs, attempt);

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
                    "Text service call failed for asset {AssetId} (attempt {Attempt}/{Total}); retrying in {Delay}s",
                    assetId, attempt, attempts, delay.TotalSeconds);
                await Task.Delay(delay, cancellationToken);
            }
        }

        throw new InvalidOperationException(
            $"Text service call failed after {attempts} attempts for asset {assetId}", lastException);
    }
}
