using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Pgvector;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

/// <summary>
/// Orchestrates face detection for a single asset: resolves the input image
/// (preferring a Large thumbnail to keep I/O low), invokes the Python service,
/// and idempotently persists detected faces. Manually-assigned faces are never
/// overwritten by this service.
/// </summary>
public class FaceDetectionService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly IFaceRecognitionClient _client;
    private readonly FaceClusteringService _clustering;
    private readonly SettingsService _settings;
    private readonly FaceRecognitionOptions _options;
    private readonly ILogger<FaceDetectionService> _logger;

    public FaceDetectionService(
        ApplicationDbContext dbContext,
        IFaceRecognitionClient client,
        FaceClusteringService clustering,
        SettingsService settings,
        IOptions<FaceRecognitionOptions> options,
        ILogger<FaceDetectionService> logger)
    {
        _dbContext = dbContext;
        _client = client;
        _clustering = clustering;
        _settings = settings;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<int> DetectAndStoreAsync(Guid assetId, CancellationToken cancellationToken)
    {
        if (!_options.Enabled || !await IsRuntimeEnabledAsync())
        {
            _logger.LogDebug("Face recognition disabled; skipping asset {AssetId}", assetId);
            return 0;
        }

        var asset = await _dbContext.Assets
            .Include(a => a.Thumbnails)
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken)
            ?? throw new InvalidOperationException($"Asset {assetId} not found");

        if (asset.Type != AssetType.Image)
        {
            _logger.LogDebug("Asset {AssetId} is not an image; skipping face detection", assetId);
            return 0;
        }

        var imagePath = await ResolveImagePathAsync(asset);
        if (string.IsNullOrEmpty(imagePath) || !File.Exists(imagePath))
        {
            throw new FileNotFoundException($"No usable image file for asset {assetId}");
        }

        var response = await _client.DetectAsync(imagePath, assetId, cancellationToken);

        // Idempotency: replace previously auto-detected faces but preserve any face
        // the user has manually assigned or rejected.
        var existing = await _dbContext.Faces
            .Where(f => f.AssetId == assetId && !f.IsManuallyAssigned && !f.IsRejected)
            .ToListAsync(cancellationToken);
        if (existing.Count > 0)
        {
            _dbContext.Faces.RemoveRange(existing);
        }

        var inserted = 0;
        foreach (var f in response.Faces)
        {
            if (f.DetScore < _options.MinDetectionScore) continue;
            if (f.Embedding.Length != 512)
            {
                _logger.LogWarning("Asset {AssetId}: face embedding has unexpected length {Len}, skipping", assetId, f.Embedding.Length);
                continue;
            }
            if (f.Bbox.Length != 4) continue;

            var face = new Face
            {
                AssetId = assetId,
                BoundingBoxX = f.Bbox[0],
                BoundingBoxY = f.Bbox[1],
                BoundingBoxW = f.Bbox[2],
                BoundingBoxH = f.Bbox[3],
                Confidence = f.DetScore,
                Embedding = new Vector(f.Embedding),
            };
            _dbContext.Faces.Add(face);
            inserted++;
        }

        asset.FaceDetectionCompletedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync(cancellationToken);

        if (inserted > 0 && asset.OwnerId.HasValue)
        {
            // Online assignment: try to attach each new orphan face to an existing
            // Person of the same owner via cosine similarity.
            await _clustering.AssignNewFacesAsync(asset.OwnerId.Value, assetId, cancellationToken);

            // Bootstrap & refresh: faces that didn't match any existing Person
            // are still orphan. Run a cooldown-guarded batch pass so new clusters
            // get created without waiting for a manual trigger or nightly job.
            await _clustering.MaybeRunBatchAsync(asset.OwnerId.Value, cancellationToken);
        }

        _logger.LogInformation("Stored {Inserted} faces for asset {AssetId}", inserted, assetId);
        return inserted;
    }

    /// <summary>
    /// Runtime override read from the Settings table. Lets an admin disable
    /// face recognition without restarting the API (the appsettings.json
    /// FaceRecognition:Enabled is the static fallback / default).
    /// </summary>
    private async Task<bool> IsRuntimeEnabledAsync()
    {
        var v = await _settings.GetSettingAsync("FaceRecognition.Enabled", Guid.Empty, "true");
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
