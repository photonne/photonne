namespace Photonne.Server.Api.Shared.Services.TextRecognition;

public class TextRecognitionOptions
{
    // Nested under the shared "Ml" section. Transport settings (ServiceUrl,
    // TimeoutSeconds, MaxRetries) live on MlOptions and are reused across
    // every ML capability.
    public const string SectionName = "Ml:TextRecognition";

    public bool Enabled { get; set; } = true;

    // Drop recognized lines below this confidence when persisting. The Python
    // service already enforces TEXT_MIN_SCORE; this is the .NET-side ceiling
    // (e.g. an admin can be more conservative than the engine default).
    public float MinScore { get; set; } = 0.5f;

    // Hard cap of stored lines per asset. The Python service caps at
    // TEXT_MAX_LINES (default 200); this is a defensive ceiling.
    public int MaxLinesPerAsset { get; set; } = 200;

    // When true, prefer the Large thumbnail over the original asset to keep
    // I/O low. RapidOCR resizes internally to ~960 px short-side anyway, so
    // running on the Large thumbnail (typically 1024 px) costs little
    // accuracy and saves the JPEG decode of the full-resolution original.
    public bool PreferThumbnailLarge { get; set; } = true;
}
