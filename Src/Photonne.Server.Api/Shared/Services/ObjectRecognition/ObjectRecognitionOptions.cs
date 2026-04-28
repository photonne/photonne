namespace Photonne.Server.Api.Shared.Services.ObjectRecognition;

public class ObjectRecognitionOptions
{
    // Nested under the shared "Ml" section. Transport settings (ServiceUrl,
    // TimeoutSeconds, MaxRetries) live on MlOptions and are reused across
    // every ML capability.
    public const string SectionName = "Ml:ObjectRecognition";

    public bool Enabled { get; set; } = true;

    // Per-class confidence threshold re-applied in the API. Lets an admin
    // tighten the bar without redeploying the Python service.
    public float MinScore { get; set; } = 0.25f;

    // Detections whose normalized side length is below this fraction of the
    // image are dropped. Filters out tiny/false-positive boxes that aren't
    // actionable as tags.
    public float MinNormalizedSize { get; set; } = 0.02f;

    // Hard cap of stored detections per asset.
    public int MaxObjectsPerAsset { get; set; } = 50;

    // When true, prefer the Large thumbnail over the original asset to keep
    // I/O low. Object detection works at 640x640 anyway.
    public bool PreferThumbnailLarge { get; set; } = true;
}
