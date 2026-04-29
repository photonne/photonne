using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.SceneClassification;

/// <summary>
/// Orchestrates scene classification for a single asset: resolves the input
/// image (preferring a Large thumbnail to keep I/O low), invokes the Python
/// service, and replaces the previous predictions idempotently.
/// </summary>
public class SceneClassificationService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly ISceneClassificationClient _client;
    private readonly SettingsService _settings;
    private readonly SceneClassificationOptions _options;
    private readonly ILogger<SceneClassificationService> _logger;

    public SceneClassificationService(
        ApplicationDbContext dbContext,
        ISceneClassificationClient client,
        SettingsService settings,
        IOptions<SceneClassificationOptions> options,
        ILogger<SceneClassificationService> logger)
    {
        _dbContext = dbContext;
        _client = client;
        _settings = settings;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<int> ClassifyAndStoreAsync(Guid assetId, CancellationToken cancellationToken)
    {
        if (!_options.Enabled || !await IsRuntimeEnabledAsync())
        {
            _logger.LogDebug("Scene classification disabled; skipping asset {AssetId}", assetId);
            return 0;
        }

        var asset = await _dbContext.Assets
            .Include(a => a.Thumbnails)
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken)
            ?? throw new InvalidOperationException($"Asset {assetId} not found");

        if (asset.Type != AssetType.Image)
        {
            _logger.LogDebug("Asset {AssetId} is not an image; skipping scene classification", assetId);
            return 0;
        }

        var imagePath = await ResolveImagePathAsync(asset);
        if (string.IsNullOrEmpty(imagePath) || !File.Exists(imagePath))
        {
            throw new FileNotFoundException($"No usable image file for asset {assetId}");
        }

        var response = await _client.ClassifyAsync(imagePath, assetId, cancellationToken);

        // Idempotency: replace all prior auto-classified scenes for this asset.
        // Predictions are not user-curated so a full refresh is the simplest
        // correct semantics — and matches how AssetDetectedObjects behave.
        var existing = await _dbContext.AssetClassifiedScenes
            .Where(s => s.AssetId == assetId)
            .ToListAsync(cancellationToken);
        if (existing.Count > 0)
        {
            _dbContext.AssetClassifiedScenes.RemoveRange(existing);
        }

        var inserted = 0;
        // Order by rank so the persisted Rank column matches the ML service's
        // ranking even if the JSON arrives out of order.
        foreach (var s in response.Scenes.OrderBy(s => s.Rank))
        {
            if (inserted >= _options.MaxScenesPerAsset) break;
            // Always keep rank 1 — the model's best guess is useful even when
            // the softmax is diffuse. Filter rank ≥ 2 by confidence so we
            // don't pollute the search facet with "maybe a ball pit (3%)".
            if (s.Rank > 1 && s.Score < _options.MinScore) continue;
            if (string.IsNullOrWhiteSpace(s.Label)) continue;

            _dbContext.AssetClassifiedScenes.Add(new AssetClassifiedScene
            {
                AssetId = assetId,
                Label = s.Label.Length > 100 ? s.Label[..100] : s.Label,
                ClassId = s.ClassId,
                Confidence = s.Score,
                Rank = s.Rank,
            });
            inserted++;
        }

        asset.SceneClassificationCompletedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Stored {Inserted} scene classifications for asset {AssetId}", inserted, assetId);
        return inserted;
    }

    /// <summary>
    /// Runtime override read from the Settings table. Lets an admin disable
    /// scene classification without restarting the API (the appsettings.json
    /// SceneClassification:Enabled is the static fallback / default).
    /// </summary>
    private async Task<bool> IsRuntimeEnabledAsync()
    {
        var v = await _settings.GetSettingAsync("SceneClassification.Enabled", Guid.Empty, "true");
        return !v.Equals("false", StringComparison.OrdinalIgnoreCase);
    }

    private async Task<string?> ResolveImagePathAsync(Asset asset)
    {
        if (_options.PreferThumbnailLarge)
        {
            var large = asset.Thumbnails.FirstOrDefault(t => t.Size == ThumbnailSize.Large);
            if (large != null && File.Exists(large.FilePath))
            {
                return large.FilePath;
            }
        }

        var physical = await _settings.ResolvePhysicalPathAsync(asset.FullPath);
        return physical;
    }
}
