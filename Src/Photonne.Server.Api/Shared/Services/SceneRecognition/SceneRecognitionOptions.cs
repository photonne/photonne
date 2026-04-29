namespace Photonne.Server.Api.Shared.Services.SceneRecognition;

public class SceneRecognitionOptions
{
    // Nested under the shared "Ml" section. Transport settings (ServiceUrl,
    // TimeoutSeconds, MaxRetries) live on MlOptions and are reused across
    // every ML capability.
    public const string SectionName = "Ml:SceneRecognition";

    public bool Enabled { get; set; } = true;

    // Drop predictions below this softmax probability when persisting. Top-1
    // is always persisted regardless so callers can see the model's best
    // guess even on ambiguous images.
    public float MinScore { get; set; } = 0.15f;

    // Hard cap of stored predictions per asset. The Python service already
    // limits to SCENE_TOP_K (default 3); this is a defensive ceiling.
    public int MaxScenesPerAsset { get; set; } = 5;

    // When true, prefer the Large thumbnail over the original asset to keep
    // I/O low. Scene classification only sees a 224x224 crop anyway.
    public bool PreferThumbnailLarge { get; set; } = true;
}
