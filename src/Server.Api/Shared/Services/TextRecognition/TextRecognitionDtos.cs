using System.Text.Json.Serialization;

namespace Photonne.Server.Api.Shared.Services.TextRecognition;

public sealed class TextDetectRequestDto
{
    [JsonPropertyName("image_path")]
    public string ImagePath { get; set; } = string.Empty;

    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }
}

public sealed class RecognizedTextLineDto
{
    [JsonPropertyName("text")]
    public string Text { get; set; } = string.Empty;

    [JsonPropertyName("confidence")]
    public float Confidence { get; set; }

    // [x, y, w, h] normalized to [0,1].
    [JsonPropertyName("bbox")]
    public float[] BBox { get; set; } = new float[4];

    [JsonPropertyName("line_index")]
    public int LineIndex { get; set; }
}

public sealed class TextDetectResponse
{
    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }

    [JsonPropertyName("lines")]
    public List<RecognizedTextLineDto> Lines { get; set; } = new();

    [JsonPropertyName("full_text")]
    public string FullText { get; set; } = string.Empty;

    // [width, height] in pixels.
    [JsonPropertyName("image_size")]
    public int[] ImageSize { get; set; } = new int[2];

    [JsonPropertyName("elapsed_ms")]
    public int ElapsedMs { get; set; }
}
