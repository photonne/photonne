using System.Text.Json.Serialization;

namespace Photonne.Server.Api.Shared.Services.Embeddings;

public sealed class EmbedImageRequestDto
{
    [JsonPropertyName("image_path")]
    public string ImagePath { get; set; } = string.Empty;

    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }
}

public sealed class EmbedTextRequestDto
{
    [JsonPropertyName("text")]
    public string Text { get; set; } = string.Empty;
}

public sealed class EmbeddingResponseDto
{
    [JsonPropertyName("asset_id")]
    public string? AssetId { get; set; }

    [JsonPropertyName("embedding")]
    public float[] Embedding { get; set; } = Array.Empty<float>();

    [JsonPropertyName("dim")]
    public int Dim { get; set; }

    [JsonPropertyName("model")]
    public string Model { get; set; } = string.Empty;

    [JsonPropertyName("elapsed_ms")]
    public int ElapsedMs { get; set; }
}
