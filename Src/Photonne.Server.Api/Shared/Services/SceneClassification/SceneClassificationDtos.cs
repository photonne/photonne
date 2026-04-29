using System.Text.Json.Serialization;

namespace Photonne.Server.Api.Shared.Services.SceneClassification;

public sealed class SceneClassifyRequestDto
{
    [JsonPropertyName("image_path")]
    public string ImagePath { get; set; } = string.Empty;

    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }
}

public sealed class ClassifiedSceneDto
{
    [JsonPropertyName("label")]
    public string Label { get; set; } = string.Empty;

    [JsonPropertyName("class_id")]
    public int ClassId { get; set; }

    [JsonPropertyName("score")]
    public float Score { get; set; }

    [JsonPropertyName("rank")]
    public int Rank { get; set; }
}

public sealed class SceneClassificationResponse
{
    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }

    [JsonPropertyName("scenes")]
    public List<ClassifiedSceneDto> Scenes { get; set; } = new();

    // [width, height] in pixels.
    [JsonPropertyName("image_size")]
    public int[] ImageSize { get; set; } = new int[2];

    [JsonPropertyName("elapsed_ms")]
    public int ElapsedMs { get; set; }
}
