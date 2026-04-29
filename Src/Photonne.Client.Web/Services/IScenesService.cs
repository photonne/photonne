namespace Photonne.Client.Web.Services;

public interface IScenesService
{
    /// <summary>Lists every ML-classified scene on a single asset, ordered by rank (1 = best guess).</summary>
    Task<List<ClassifiedSceneItem>> GetScenesForAssetAsync(Guid assetId, CancellationToken ct = default);

    /// <summary>Distinct scene labels in the caller's catalog with the number of distinct
    /// assets that contain each one. Powers facets and autocomplete.</summary>
    Task<List<SceneLabelItem>> GetLabelsAsync(string? search = null, int limit = 200, CancellationToken ct = default);
}

public sealed record ClassifiedSceneItem(
    Guid Id,
    string Label,
    int ClassId,
    float Confidence,
    int Rank);

public sealed record SceneLabelItem(string Label, int AssetCount);
