using System.Net.Http.Json;
using Microsoft.Extensions.Options;

namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

public class FaceRecognitionClient : IFaceRecognitionClient
{
    private readonly HttpClient _http;
    private readonly FaceRecognitionOptions _options;
    private readonly ILogger<FaceRecognitionClient> _logger;

    public FaceRecognitionClient(HttpClient http, IOptions<FaceRecognitionOptions> options, ILogger<FaceRecognitionClient> logger)
    {
        _http = http;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<FaceDetectionResponse> DetectAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default)
    {
        var req = new DetectRequestDto { ImagePath = imagePath, AssetId = assetId.ToString() };

        var attempts = Math.Max(1, _options.MaxRetries + 1);
        Exception? lastException = null;

        for (var attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                using var response = await _http.PostAsJsonAsync("/v1/faces/detect", req, cancellationToken);

                if (!response.IsSuccessStatusCode)
                {
                    var body = await response.Content.ReadAsStringAsync(cancellationToken);
                    var status = (int)response.StatusCode;
                    // 4xx (except 408/429) are not transient — fail fast.
                    if (status is >= 400 and < 500 && status is not 408 and not 429)
                    {
                        throw new HttpRequestException(
                            $"Face service returned {status} for asset {assetId}: {body}");
                    }
                    throw new HttpRequestException(
                        $"Face service returned transient {status} for asset {assetId}: {body}");
                }

                var dto = await response.Content.ReadFromJsonAsync<FaceDetectionResponse>(cancellationToken: cancellationToken)
                    ?? throw new InvalidOperationException($"Empty response from face service for asset {assetId}");

                _logger.LogInformation(
                    "Face service detected {Count} faces for asset {AssetId} in {Elapsed} ms (attempt {Attempt})",
                    dto.Faces.Count, assetId, dto.ElapsedMs, attempt);

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
                    "Face service call failed for asset {AssetId} (attempt {Attempt}/{Total}); retrying in {Delay}s",
                    assetId, attempt, attempts, delay.TotalSeconds);
                await Task.Delay(delay, cancellationToken);
            }
        }

        throw new InvalidOperationException(
            $"Face service call failed after {attempts} attempts for asset {assetId}", lastException);
    }
}
