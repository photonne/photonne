using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Embeddings;
using Photonne.Server.Api.Shared.Services.FaceRecognition;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>What the ML service can do for a summary right now.</summary>
public sealed record MlCapabilities(bool Faces, bool Embeddings, bool Voice)
{
    public static readonly MlCapabilities None = new(false, false, false);
    public bool Any => Faces || Embeddings || Voice;
}

/// <summary>A face found in a frame: where (as a share of the frame) and who (embedding).</summary>
public sealed record FrameFace(float Area, float Score, float[] Embedding);

/// <summary>
/// The ML calls the video summaries make, behind one seam: availability
/// first (so a summary on a server without the ML service — or with a
/// capability switched off — doesn't spend minutes on retries), then one call
/// per frame or audio track. Every call returns null instead of throwing:
/// ML only refines a summary, it never fails one.
/// </summary>
public interface IVideoMlService
{
    Task<MlCapabilities> CapabilitiesAsync(CancellationToken ct);
    Task<IReadOnlyList<FrameFace>?> FacesAsync(string imagePath, Guid assetId, CancellationToken ct);
    Task<float[]?> EmbedImageAsync(string imagePath, Guid assetId, CancellationToken ct);
    Task<float[]?> EmbedTextAsync(string text, CancellationToken ct);
    /// <summary>Stretches with someone speaking, [start, end] in seconds.</summary>
    Task<IReadOnlyList<double[]>?> SpeechAsync(string wavPath, Guid assetId, CancellationToken ct);
}

public sealed class VideoMlService : IVideoMlService
{
    private static readonly TimeSpan HealthTimeout = TimeSpan.FromSeconds(5);

    private readonly HttpClient _http;
    private readonly MlOptions _options;
    private readonly IFaceRecognitionClient _faces;
    private readonly IEmbeddingClient _embeddings;
    private readonly MlEnablement _enablement;
    private readonly ILogger<VideoMlService> _logger;

    public VideoMlService(
        HttpClient http,
        IOptions<MlOptions> options,
        IFaceRecognitionClient faces,
        IEmbeddingClient embeddings,
        MlEnablement enablement,
        ILogger<VideoMlService> logger)
    {
        _http = http;
        _options = options.Value;
        _faces = faces;
        _embeddings = embeddings;
        _enablement = enablement;
        _logger = logger;
    }

    public async Task<MlCapabilities> CapabilitiesAsync(CancellationToken ct)
    {
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
            timeout.CancelAfter(HealthTimeout);
            var health = await _http.GetFromJsonAsync<HealthDto>("/health", timeout.Token);
            bool Loaded(string name) => health?.Components?.TryGetValue(name, out var c) == true && c.Enabled && c.Loaded;

            return new MlCapabilities(
                Loaded("faces") && await _enablement.IsEnabledAsync(AssetEnrichmentType.FaceRecognition),
                Loaded("embeddings") && await _enablement.IsEnabledAsync(AssetEnrichmentType.ImageEmbedding),
                Loaded("voice"));
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception ex)
        {
            _logger.LogInformation("ML service not available for video summaries: {Reason}", ex.Message);
            return MlCapabilities.None;
        }
    }

    public Task<IReadOnlyList<FrameFace>?> FacesAsync(string imagePath, Guid assetId, CancellationToken ct) =>
        Quietly<IReadOnlyList<FrameFace>>(async () =>
        {
            var response = await _faces.DetectAsync(imagePath, assetId, ct);
            return response.Faces
                .Where(f => f.Embedding.Length > 0 && f.Bbox.Length == 4)
                .Select(f => new FrameFace(f.Bbox[2] * f.Bbox[3], f.DetScore, f.Embedding))
                .ToList();
        }, "faces", ct);

    public Task<float[]?> EmbedImageAsync(string imagePath, Guid assetId, CancellationToken ct) =>
        Quietly(async () => (await _embeddings.EmbedImageAsync(imagePath, assetId, ct)).Embedding, "image embedding", ct);

    public Task<float[]?> EmbedTextAsync(string text, CancellationToken ct) =>
        Quietly(async () => (await _embeddings.EmbedTextAsync(text, ct)).Embedding, "text embedding", ct);

    public Task<IReadOnlyList<double[]>?> SpeechAsync(string wavPath, Guid assetId, CancellationToken ct) =>
        Quietly<IReadOnlyList<double[]>>(async () =>
        {
            var request = new VoiceRequestDto { AudioPath = wavPath.Replace('\\', '/'), AssetId = assetId.ToString() };
            var response = await MlCall.PostAsync<VoiceRequestDto, VoiceResponseDto>(
                _http, _options, _logger, "/v1/audio/voice", request, "Detección de voz", $"asset {assetId}", ct);
            return response.Segments.Where(s => s.Length == 2).ToList();
        }, "voice", ct);

    private async Task<T?> Quietly<T>(Func<Task<T>> call, string what, CancellationToken ct) where T : class
    {
        try
        {
            return await call();
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Video summary ML call ({What}) failed: {Reason}", what, ex.Message);
            return null;
        }
    }

    private sealed class HealthDto
    {
        [JsonPropertyName("components")]
        public Dictionary<string, HealthComponentDto>? Components { get; set; }
    }

    private sealed class HealthComponentDto
    {
        [JsonPropertyName("enabled")] public bool Enabled { get; set; }
        [JsonPropertyName("loaded")] public bool Loaded { get; set; }
    }

    private sealed class VoiceRequestDto
    {
        [JsonPropertyName("audio_path")] public string AudioPath { get; set; } = string.Empty;
        [JsonPropertyName("asset_id")] public string? AssetId { get; set; }
    }

    private sealed class VoiceResponseDto
    {
        [JsonPropertyName("segments")] public List<double[]> Segments { get; set; } = new();
        [JsonPropertyName("duration")] public double Duration { get; set; }
    }
}
