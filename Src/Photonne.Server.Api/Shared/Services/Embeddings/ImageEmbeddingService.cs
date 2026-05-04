using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Pgvector;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.Embeddings;

/// <summary>
/// Orchestrates CLIP image embedding for a single asset: resolves the input
/// image (preferring a Large thumbnail to keep I/O low), invokes the Python
/// service, and upserts the row in <c>AssetEmbeddings</c> idempotently. Skips
/// re-encoding when an existing row's <c>ModelVersion</c> matches the active
/// configuration — versions are bumped in lock-step on the Python and .NET
/// sides whenever the model is swapped.
/// </summary>
public class ImageEmbeddingService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly IEmbeddingClient _client;
    private readonly SettingsService _settings;
    private readonly EmbeddingOptions _options;
    private readonly ILogger<ImageEmbeddingService> _logger;

    public ImageEmbeddingService(
        ApplicationDbContext dbContext,
        IEmbeddingClient client,
        SettingsService settings,
        IOptions<EmbeddingOptions> options,
        ILogger<ImageEmbeddingService> logger)
    {
        _dbContext = dbContext;
        _client = client;
        _settings = settings;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<bool> EmbedAndStoreAsync(Guid assetId, CancellationToken cancellationToken)
    {
        if (!_options.Enabled || !await IsRuntimeEnabledAsync())
        {
            _logger.LogDebug("Image embedding disabled; skipping asset {AssetId}", assetId);
            return false;
        }

        var asset = await _dbContext.Assets
            .Include(a => a.Thumbnails)
            .Include(a => a.Embedding)
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken)
            ?? throw new InvalidOperationException($"Asset {assetId} not found");

        if (asset.Type != AssetType.Image)
        {
            _logger.LogDebug("Asset {AssetId} is not an image; skipping image embedding", assetId);
            return false;
        }

        // Idempotency: skip if we already have a row for the active model
        // version. A model bump (different ModelVersion) re-encodes via the
        // backfill admin endpoint.
        if (asset.Embedding != null && asset.Embedding.ModelVersion == _options.ModelVersion)
        {
            asset.ImageEmbeddingCompletedAt = DateTime.UtcNow;
            await _dbContext.SaveChangesAsync(cancellationToken);
            _logger.LogDebug(
                "Asset {AssetId} already has embedding for model {Model}; skipping",
                assetId, _options.ModelVersion);
            return false;
        }

        var imagePath = await ResolveImagePathAsync(asset);
        if (string.IsNullOrEmpty(imagePath) || !File.Exists(imagePath))
        {
            throw new FileNotFoundException($"No usable image file for asset {assetId}");
        }

        var response = await _client.EmbedImageAsync(imagePath, assetId, cancellationToken);

        if (response.Embedding == null || response.Embedding.Length == 0)
        {
            throw new InvalidOperationException($"Empty embedding returned for asset {assetId}");
        }

        var vector = new Vector(response.Embedding);

        if (asset.Embedding == null)
        {
            _dbContext.AssetEmbeddings.Add(new AssetEmbedding
            {
                AssetId = assetId,
                Embedding = vector,
                ModelVersion = response.Model,
                CreatedAt = DateTime.UtcNow,
            });
        }
        else
        {
            asset.Embedding.Embedding = vector;
            asset.Embedding.ModelVersion = response.Model;
            asset.Embedding.CreatedAt = DateTime.UtcNow;
        }

        asset.ImageEmbeddingCompletedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation(
            "Stored image embedding for asset {AssetId} (model={Model}, dim={Dim})",
            assetId, response.Model, response.Dim);
        return true;
    }

    private async Task<bool> IsRuntimeEnabledAsync()
    {
        var v = await _settings.GetSettingAsync("Embedding.Enabled", Guid.Empty, "true");
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
