namespace Photonne.Client.Web.Services;

public interface IObjectsService
{
    /// <summary>Lists every ML-detected object on a single asset, ordered by confidence.</summary>
    Task<List<DetectedObjectItem>> GetObjectsForAssetAsync(Guid assetId, CancellationToken ct = default);

    /// <summary>Distinct object labels in the caller's catalog with the number of distinct
    /// assets that contain each one. Powers facets and autocomplete.</summary>
    Task<List<ObjectLabelItem>> GetLabelsAsync(string? search = null, int limit = 200, CancellationToken ct = default);
}

public sealed record DetectedObjectItem(
    Guid Id,
    string Label,
    int ClassId,
    float Confidence,
    float BoundingBoxX,
    float BoundingBoxY,
    float BoundingBoxW,
    float BoundingBoxH);

public sealed record ObjectLabelItem(string Label, int AssetCount);
