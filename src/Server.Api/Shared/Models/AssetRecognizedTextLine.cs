namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// One recognized text line extracted from an image by the OCR pipeline.
/// Stored as one row per line so each can keep its own bounding box and
/// confidence — and so the search index can match per-line rather than
/// having to chop a concatenated blob.
/// </summary>
public class AssetRecognizedTextLine
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    // Recognized text content. Postgres "text" column (no MaxLength) — long
    // captures (receipts, screenshots of articles) can easily blow past
    // varchar limits and there's no reason to cap it.
    public string Text { get; set; } = string.Empty;

    // Recognition confidence in [0,1] from the CRNN head. Used to filter
    // noisy detections out of the search index, not surfaced in the UI.
    public float Confidence { get; set; }

    // Axis-aligned bbox normalized to [0,1] over the original image
    // dimensions. Same convention as AssetDetectedObject so the UI overlay code
    // can be reused. The detector returns a tighter quad polygon; we keep
    // only the enclosing rectangle here to avoid a separate polygon table.
    public float BBoxX { get; set; }
    public float BBoxY { get; set; }
    public float BBoxWidth { get; set; }
    public float BBoxHeight { get; set; }

    // 0-based reading order assigned by the recognizer (top-to-bottom,
    // left-to-right within a row). Lets the UI render lines in the right
    // order without re-sorting on geometry.
    public int LineIndex { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
