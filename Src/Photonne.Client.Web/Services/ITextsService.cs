namespace Photonne.Client.Web.Services;

public interface ITextsService
{
    /// <summary>Lists every OCR-extracted text line on a single asset, in reading order.</summary>
    Task<List<ExtractedTextLineItem>> GetTextForAssetAsync(Guid assetId, CancellationToken ct = default);
}

public sealed record ExtractedTextLineItem(
    Guid Id,
    string Text,
    float Confidence,
    float BBoxX,
    float BBoxY,
    float BBoxWidth,
    float BBoxHeight,
    int LineIndex);
