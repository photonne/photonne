using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.TextRecognition;

/// <summary>
/// Orchestrates OCR for a single asset: resolves the input image (preferring
/// a Large thumbnail to keep I/O low), invokes the Python service, and
/// replaces the previously extracted lines idempotently.
/// </summary>
public class TextRecognitionService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly ITextRecognitionClient _client;
    private readonly SettingsService _settings;
    private readonly TextRecognitionOptions _options;
    private readonly ILogger<TextRecognitionService> _logger;

    public TextRecognitionService(
        ApplicationDbContext dbContext,
        ITextRecognitionClient client,
        SettingsService settings,
        IOptions<TextRecognitionOptions> options,
        ILogger<TextRecognitionService> logger)
    {
        _dbContext = dbContext;
        _client = client;
        _settings = settings;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<int> RecognizeAndStoreAsync(Guid assetId, CancellationToken cancellationToken)
    {
        if (!_options.Enabled || !await IsRuntimeEnabledAsync())
        {
            _logger.LogDebug("Text recognition disabled; skipping asset {AssetId}", assetId);
            return 0;
        }

        var asset = await _dbContext.Assets
            .Include(a => a.Thumbnails)
            .FirstOrDefaultAsync(a => a.Id == assetId, cancellationToken)
            ?? throw new InvalidOperationException($"Asset {assetId} not found");

        if (asset.Type != AssetType.Image)
        {
            _logger.LogDebug("Asset {AssetId} is not an image; skipping text recognition", assetId);
            return 0;
        }

        var imagePath = await ResolveImagePathAsync(asset);
        if (string.IsNullOrEmpty(imagePath) || !File.Exists(imagePath))
        {
            throw new FileNotFoundException($"No usable image file for asset {assetId}");
        }

        var response = await _client.DetectAsync(imagePath, assetId, cancellationToken);

        // Idempotency: replace all prior extracted text rows for this asset.
        // OCR output is not user-curated so a full refresh is the simplest
        // correct semantics — and matches Scene/Object behavior.
        var existing = await _dbContext.AssetRecognizedTextLines
            .Where(t => t.AssetId == assetId)
            .ToListAsync(cancellationToken);
        if (existing.Count > 0)
        {
            _dbContext.AssetRecognizedTextLines.RemoveRange(existing);
        }

        var inserted = 0;
        // Order by LineIndex so the persisted reading order matches the ML
        // service's even if the JSON arrived out of order.
        foreach (var line in response.Lines.OrderBy(l => l.LineIndex))
        {
            if (inserted >= _options.MaxLinesPerAsset) break;
            if (line.Confidence < _options.MinScore) continue;
            var text = (line.Text ?? string.Empty).Trim();
            if (string.IsNullOrEmpty(text)) continue;

            var bbox = line.BBox ?? Array.Empty<float>();
            float bx = bbox.Length > 0 ? bbox[0] : 0f;
            float by = bbox.Length > 1 ? bbox[1] : 0f;
            float bw = bbox.Length > 2 ? bbox[2] : 0f;
            float bh = bbox.Length > 3 ? bbox[3] : 0f;

            _dbContext.AssetRecognizedTextLines.Add(new AssetRecognizedTextLine
            {
                AssetId = assetId,
                Text = text,
                Confidence = line.Confidence,
                BBoxX = bx,
                BBoxY = by,
                BBoxWidth = bw,
                BBoxHeight = bh,
                LineIndex = inserted,
            });
            inserted++;
        }

        asset.TextRecognitionCompletedAt = DateTime.UtcNow;
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Stored {Inserted} extracted text lines for asset {AssetId}", inserted, assetId);
        return inserted;
    }

    /// <summary>
    /// Runtime override read from the Settings table. Lets an admin disable
    /// OCR without restarting the API (the appsettings.json
    /// TextRecognition:Enabled is the static fallback / default).
    /// </summary>
    private async Task<bool> IsRuntimeEnabledAsync()
    {
        var v = await _settings.GetSettingAsync("TextRecognition.Enabled", Guid.Empty, "true");
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
