using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.ObjectRecognition;

/// <summary>
/// Orchestrates object detection for a single asset: resolves the input image
/// (preferring a Large thumbnail to keep I/O low), invokes the Python service,
/// and replaces the previous detections idempotently.
/// </summary>
public class ObjectDetectionService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly IObjectRecognitionClient _client;
    private readonly SettingsService _settings;
    private readonly ObjectRecognitionOptions _options;
    private readonly ILogger<ObjectDetectionService> _logger;

    public ObjectDetectionService(
        ApplicationDbContext dbContext,
        IObjectRecognitionClient client,
        SettingsService settings,
        IOptions<ObjectRecognitionOptions> options,
        ILogger<ObjectDetectionService> logger)
    {
        _dbContext = dbContext;
        _client = client;
        _settings = settings;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<int> DetectAndStoreAsync(Guid assetId, CancellationToken cancellationToken)
    {
        if (!_options.Enabled || !await IsRuntimeEnabledAsync())
        {
            _logger.LogDebug("Object recognition disabled; skipping asset {AssetId}", assetId);
            return 0;
        }

        var asset = await _dbContext.Assets
            .Include(a => a.Thumbnails)
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken)
            ?? throw new InvalidOperationException($"Asset {assetId} not found");

        if (asset.Type != AssetType.Image)
        {
            _logger.LogDebug("Asset {AssetId} is not an image; skipping object recognition", assetId);
            return 0;
        }

        var imagePath = await ResolveImagePathAsync(asset);
        if (string.IsNullOrEmpty(imagePath) || !File.Exists(imagePath))
        {
            throw new FileNotFoundException($"No usable image file for asset {assetId}");
        }

        var response = await _client.DetectAsync(imagePath, assetId, cancellationToken);

        // Idempotency: replace all prior auto-detected objects for this asset.
        // Object detections are not user-curated like Face/Person, so a full
        // refresh is the simplest correct semantics.
        var existing = await _dbContext.ObjectDetections
            .Where(o => o.AssetId == assetId)
            .ToListAsync(cancellationToken);
        if (existing.Count > 0)
        {
            _dbContext.ObjectDetections.RemoveRange(existing);
        }

        var inserted = 0;
        foreach (var o in response.Objects.OrderByDescending(o => o.Score))
        {
            if (inserted >= _options.MaxObjectsPerAsset) break;
            if (o.Score < _options.MinScore) continue;
            if (o.Bbox.Length != 4) continue;

            var w = o.Bbox[2];
            var h = o.Bbox[3];
            if (w < _options.MinNormalizedSize || h < _options.MinNormalizedSize) continue;
            if (string.IsNullOrWhiteSpace(o.Label)) continue;

            _dbContext.ObjectDetections.Add(new ObjectDetection
            {
                AssetId = assetId,
                Label = o.Label.Length > 100 ? o.Label[..100] : o.Label,
                ClassId = o.ClassId,
                Confidence = o.Score,
                BoundingBoxX = o.Bbox[0],
                BoundingBoxY = o.Bbox[1],
                BoundingBoxW = w,
                BoundingBoxH = h,
            });
            inserted++;
        }

        asset.ObjectRecognitionCompletedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Stored {Inserted} object detections for asset {AssetId}", inserted, assetId);
        return inserted;
    }

    /// <summary>
    /// Runtime override read from the Settings table. Lets an admin disable
    /// object recognition without restarting the API (the appsettings.json
    /// ObjectRecognition:Enabled is the static fallback / default).
    /// </summary>
    private async Task<bool> IsRuntimeEnabledAsync()
    {
        var v = await _settings.GetSettingAsync("ObjectRecognition.Enabled", Guid.Empty, "true");
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
