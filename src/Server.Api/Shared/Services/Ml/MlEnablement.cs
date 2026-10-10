using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Embeddings;
using Photonne.Server.Api.Shared.Services.FaceRecognition;
using Photonne.Server.Api.Shared.Services.ObjectDetection;
using Photonne.Server.Api.Shared.Services.SceneClassification;
using Photonne.Server.Api.Shared.Services.TextRecognition;

namespace Photonne.Server.Api.Shared.Services.Ml;

/// <summary>
/// One answer to "would this enrichment actually do anything if I queued it?".
///
/// Each ML service already asks this of itself before working
/// (<c>_options.Enabled &amp;&amp; the runtime override</c>), but it answers by
/// returning early — without stamping the asset's <c>*CompletedAt</c>, which is
/// correct (nothing was analysed) and leaves the asset in the unprocessed pool
/// forever. A backfill against a disabled model therefore queues work that
/// completes instantly, changes nothing, and leaves exactly as much to do as
/// before: the admin screen shows a task that is permanently busy and
/// permanently at zero.
///
/// So the producers ask first, and refuse. Same two inputs as the services use,
/// in the same order, so the answer here can't drift from what the worker will
/// actually do.
/// </summary>
public class MlEnablement
{
    private readonly SettingsService _settings;
    private readonly FaceRecognitionOptions _face;
    private readonly ObjectDetectionOptions _objects;
    private readonly SceneClassificationOptions _scenes;
    private readonly TextRecognitionOptions _text;
    private readonly EmbeddingOptions _embedding;

    public MlEnablement(
        SettingsService settings,
        IOptions<FaceRecognitionOptions> face,
        IOptions<ObjectDetectionOptions> objects,
        IOptions<SceneClassificationOptions> scenes,
        IOptions<TextRecognitionOptions> text,
        IOptions<EmbeddingOptions> embedding)
    {
        _settings = settings;
        _face = face.Value;
        _objects = objects.Value;
        _scenes = scenes.Value;
        _text = text.Value;
        _embedding = embedding.Value;
    }

    /// <summary>
    /// True when the worker would really run this task type. Types with no
    /// feature flag (EXIF, thumbnails, media recognition) are always on.
    /// </summary>
    public async Task<bool> IsEnabledAsync(AssetEnrichmentType taskType)
    {
        var gate = GateFor(taskType);
        if (gate is null) return true;

        var (settingKey, staticallyEnabled) = gate.Value;
        if (!staticallyEnabled) return false;

        // Same default as the services: an absent setting means "on", so a
        // fresh install doesn't need five rows in the Settings table.
        var raw = await _settings.GetSettingAsync(settingKey, Guid.Empty, "true");
        return !raw.Equals("false", StringComparison.OrdinalIgnoreCase);
    }

    private (string SettingKey, bool StaticallyEnabled)? GateFor(AssetEnrichmentType taskType) => taskType switch
    {
        AssetEnrichmentType.FaceRecognition     => (FaceRecognitionService.EnabledKey,     _face.Enabled),
        AssetEnrichmentType.ObjectDetection     => (ObjectDetectionService.EnabledKey,     _objects.Enabled),
        AssetEnrichmentType.SceneClassification => (SceneClassificationService.EnabledKey, _scenes.Enabled),
        AssetEnrichmentType.TextRecognition     => (TextRecognitionService.EnabledKey,     _text.Enabled),
        // Note the key is "Embedding.Enabled", not "ImageEmbedding.Enabled" —
        // the setting predates the rename of the task type.
        AssetEnrichmentType.ImageEmbedding      => (ImageEmbeddingService.EnabledKey,      _embedding.Enabled),
        _ => null,
    };
}
