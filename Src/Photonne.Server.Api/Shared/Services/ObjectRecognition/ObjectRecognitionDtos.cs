using System.Text.Json.Serialization;

namespace Photonne.Server.Api.Shared.Services.ObjectRecognition;

public sealed class ObjectDetectRequestDto
{
    [JsonPropertyName("image_path")]
    public string ImagePath { get; set; } = string.Empty;

    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }
}

public sealed class DetectedObjectDto
{
    [JsonPropertyName("label")]
    public string Label { get; set; } = string.Empty;

    [JsonPropertyName("class_id")]
    public int ClassId { get; set; }

    [JsonPropertyName("score")]
    public float Score { get; set; }

    // [x, y, w, h] normalized to [0,1] over the source image dimensions.
    [JsonPropertyName("bbox")]
    public float[] Bbox { get; set; } = Array.Empty<float>();
}

public sealed class ObjectDetectionResponse
{
    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }

    [JsonPropertyName("objects")]
    public List<DetectedObjectDto> Objects { get; set; } = new();

    // [width, height] in pixels.
    [JsonPropertyName("image_size")]
    public int[] ImageSize { get; set; } = new int[2];

    [JsonPropertyName("elapsed_ms")]
    public int ElapsedMs { get; set; }
}
