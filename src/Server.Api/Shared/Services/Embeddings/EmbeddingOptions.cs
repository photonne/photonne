namespace Photonne.Server.Api.Shared.Services.Embeddings;

public class EmbeddingOptions
{
    // Nested under the shared "Ml" section. Transport settings (ServiceUrl,
    // TimeoutSeconds, MaxRetries) live on MlOptions and are reused across
    // every ML capability.
    public const string SectionName = "Ml:Embedding";

    public bool Enabled { get; set; } = true;

    // When true, prefer the Large thumbnail over the original asset to keep
    // I/O low. CLIP only sees a 224x224 crop anyway.
    public bool PreferThumbnailLarge { get; set; } = true;

    // Stable identifier the .NET side persists with each embedding row. Must
    // match Python's EMBEDDING_MODEL_VERSION; if a future deployment swaps
    // models, bumping this triggers a re-embed via the backfill admin endpoint
    // (rows whose ModelVersion differs are re-encoded).
    public string ModelVersion { get; set; } = "mclip-vit-b32-v1";

    // Cosine-distance ceiling for results returned by the semantic search
    // endpoint. pgvector's <=> operator returns 0 for identical vectors and
    // 2 for opposites; in practice CLIP relevant matches sit below ~0.5 and
    // anything above ~0.7 is noise. Per-query reranking can override this.
    public float MaxCosineDistance { get; set; } = 0.7f;
}
