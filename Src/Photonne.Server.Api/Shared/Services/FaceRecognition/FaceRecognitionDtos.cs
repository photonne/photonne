using System.Text.Json.Serialization;

namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

public sealed class DetectRequestDto
{
    [JsonPropertyName("image_path")]
    public string ImagePath { get; set; } = string.Empty;

    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }
}

public sealed class DetectedFaceDto
{
    // [x, y, w, h] normalized to [0,1] over the source image dimensions.
    [JsonPropertyName("bbox")]
    public float[] Bbox { get; set; } = Array.Empty<float>();

    [JsonPropertyName("det_score")]
    public float DetScore { get; set; }

    [JsonPropertyName("embedding")]
    public float[] Embedding { get; set; } = Array.Empty<float>();

    [JsonPropertyName("landmarks_5")]
    public float[][]? Landmarks5 { get; set; }
}

public sealed class FaceRecognitionResponse
{
    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }

    [JsonPropertyName("faces")]
    public List<DetectedFaceDto> Faces { get; set; } = new();

    // [width, height] in pixels.
    [JsonPropertyName("image_size")]
    public int[] ImageSize { get; set; } = new int[2];

    [JsonPropertyName("elapsed_ms")]
    public int ElapsedMs { get; set; }
}
