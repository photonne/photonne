using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Populates the public demo with a curated set of photos/videos on first boot.
///
/// Flow when <see cref="DemoModeOptions.Enabled"/> is true and no demo user exists yet:
///   1. Create the shared `demo` user.
///   2. Copy every media file from <see cref="DemoModeOptions.SeedPath"/> on the host into
///      <c>/data/assets/users/{demoUserId}/demo-seed/</c>.
///   3. Walk the copied files through <see cref="AssetIndexingService"/> so EXIF, thumbnails,
///      folders and ML jobs are all created.
///   4. Create a handful of sample albums + mark some assets as favourites so the UI
///      feels alive on first visit.
///
/// Exposed as a hosted service (auto-runs on startup) and as a reusable method
/// (<see cref="SeedAsync"/>) used by <c>DemoResetService</c>.
/// </summary>
public sealed class DemoSeederService : IHostedService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly IOptions<DemoModeOptions> _options;
    private readonly ILogger<DemoSeederService> _logger;

    private const string DemoFolderName = "demo-seed";

    public DemoSeederService(
        IServiceProvider serviceProvider,
        IOptions<DemoModeOptions> options,
        ILogger<DemoSeederService> logger)
    {
        _serviceProvider = serviceProvider;
        _options = options;
        _logger = logger;
    }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        if (!_options.Value.Enabled)
            return Task.CompletedTask;

        // Run seed out-of-band so the web server can start serving traffic (including
        // `/api/admin/demo-info` and the login page) immediately. A cold seed on a fresh
        // DB can take minutes when thumbnails are generated for every asset.
        _ = Task.Run(async () =>
        {
            try
            {
                await SeedAsync(cancellationToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "[DEMO] Seeding failed");
            }
        }, cancellationToken);

        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;

    /// <summary>
    /// Idempotent seed: if the demo user already exists and has assets, this is a no-op.
    /// Callable from the reset service to re-hydrate the demo after a wipe.
    /// </summary>
    public async Task SeedAsync(CancellationToken cancellationToken)
    {
        using var scope = _serviceProvider.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var authService = scope.ServiceProvider.GetRequiredService<IAuthService>();
        var indexingService = scope.ServiceProvider.GetRequiredService<AssetIndexingService>();
        var settingsService = scope.ServiceProvider.GetRequiredService<SettingsService>();

        var opts = _options.Value;

        // 1) Demo user
        var demoUser = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Username == opts.DemoUsername, cancellationToken);

        if (demoUser == null)
        {
            demoUser = new User
            {
                Username = opts.DemoUsername,
                Email = opts.DemoEmail,
                PasswordHash = authService.HashPassword(opts.DemoPassword),
                FirstName = "Demo",
                LastName = "User",
                Role = "User",
                IsActive = true,
                IsEmailVerified = true,
                IsPrimaryAdmin = false,
                CreatedAt = DateTime.UtcNow
            };
            dbContext.Users.Add(demoUser);
            await dbContext.SaveChangesAsync(cancellationToken);
            _logger.LogInformation("[DEMO] Created shared demo user '{User}' (id={Id})",
                demoUser.Username, demoUser.Id);
        }

        // Skip if already seeded — idempotent guard.
        var hasAssets = await dbContext.Assets
            .AnyAsync(a => a.OwnerId == demoUser.Id && a.DeletedAt == null, cancellationToken);
        if (hasAssets)
        {
            _logger.LogInformation("[DEMO] Demo user already has assets — skipping seed.");
            return;
        }

        // 2) Copy seed files into the user's space
        if (!Directory.Exists(opts.SeedPath))
        {
            _logger.LogWarning(
                "[DEMO] Seed directory not found at '{Path}' — skipping asset seed. " +
                "Mount your seed images there (see docker-compose.demo.yml).",
                opts.SeedPath);
            return;
        }

        var internalAssetsRoot = settingsService.GetInternalAssetsPath();
        var userAssetsRoot = Path.Combine(
            internalAssetsRoot, "users", demoUser.Id.ToString(), DemoFolderName);
        Directory.CreateDirectory(userAssetsRoot);

        var copied = CopyDirectoryContents(opts.SeedPath, userAssetsRoot, cancellationToken);
        if (copied.Count == 0)
        {
            _logger.LogWarning("[DEMO] No files copied from '{Path}'. Seed skipped.", opts.SeedPath);
            return;
        }

        _logger.LogInformation("[DEMO] Copied {Count} files from '{Src}' to '{Dst}'",
            copied.Count, opts.SeedPath, userAssetsRoot);

        // 3) Index every copied file. Uses the existing pipeline so EXIF, thumbnails
        //    and tag detection all run the same as a normal upload.
        var indexedAssets = new List<Asset>();
        foreach (var filePath in copied)
        {
            cancellationToken.ThrowIfCancellationRequested();
            try
            {
                var asset = await indexingService.IndexFileAsync(filePath, demoUser.Id, cancellationToken);
                if (asset != null)
                    indexedAssets.Add(asset);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "[DEMO] Failed to index seed file '{Path}'", filePath);
            }
        }

        _logger.LogInformation("[DEMO] Indexed {Count}/{Total} seed files",
            indexedAssets.Count, copied.Count);

        if (indexedAssets.Count == 0)
            return;

        // 4) Sample albums + favourites so the UI is not empty
        await CreateSampleAlbumsAsync(dbContext, demoUser.Id, indexedAssets, cancellationToken);
        await MarkSomeFavouritesAsync(dbContext, indexedAssets, cancellationToken);
    }

    /// <summary>
    /// Copies all files from <paramref name="source"/> into <paramref name="destination"/>,
    /// preserving sub-directory structure. Returns the list of destination file paths.
    /// Existing files at the destination are overwritten.
    /// </summary>
    private static List<string> CopyDirectoryContents(
        string source, string destination, CancellationToken ct)
    {
        var copied = new List<string>();

        foreach (var sourceFile in Directory.EnumerateFiles(source, "*", SearchOption.AllDirectories))
        {
            ct.ThrowIfCancellationRequested();

            var relative = Path.GetRelativePath(source, sourceFile);
            var destFile = Path.Combine(destination, relative);
            var destDir = Path.GetDirectoryName(destFile);
            if (!string.IsNullOrEmpty(destDir))
                Directory.CreateDirectory(destDir);

            File.Copy(sourceFile, destFile, overwrite: true);
            copied.Add(destFile);
        }

        return copied;
    }

    private async Task CreateSampleAlbumsAsync(
        ApplicationDbContext dbContext,
        Guid ownerId,
        IReadOnlyList<Asset> assets,
        CancellationToken ct)
    {
        // Split the assets into 3 roughly equal buckets. Using modulo keeps the distribution
        // deterministic regardless of scan order.
        var definitions = new[]
        {
            new { Name = "Mejores momentos", Description = "Una selección destacada de la biblioteca demo." },
            new { Name = "Viajes",           Description = "Fotos con geolocalización." },
            new { Name = "Recientes",        Description = "Últimos assets añadidos." }
        };

        for (var bucket = 0; bucket < definitions.Length; bucket++)
        {
            var bucketAssets = assets
                .Where((_, idx) => idx % definitions.Length == bucket)
                .Take(20)
                .ToList();

            if (bucketAssets.Count == 0)
                continue;

            var album = new Album
            {
                Name = definitions[bucket].Name,
                Description = definitions[bucket].Description,
                OwnerId = ownerId,
                CoverAssetId = bucketAssets[0].Id,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };
            dbContext.Albums.Add(album);
            await dbContext.SaveChangesAsync(ct);

            var order = 0;
            foreach (var asset in bucketAssets)
            {
                dbContext.AlbumAssets.Add(new AlbumAsset
                {
                    AlbumId = album.Id,
                    AssetId = asset.Id,
                    Order = order++,
                    AddedAt = DateTime.UtcNow
                });
            }
            await dbContext.SaveChangesAsync(ct);

            _logger.LogInformation("[DEMO] Created album '{Name}' with {Count} assets",
                album.Name, bucketAssets.Count);
        }
    }

    private static async Task MarkSomeFavouritesAsync(
        ApplicationDbContext dbContext,
        IReadOnlyList<Asset> assets,
        CancellationToken ct)
    {
        // Roughly 20% of assets, capped at 15, deterministically spread along the list.
        var favouriteCount = Math.Min(15, Math.Max(1, assets.Count / 5));
        var step = Math.Max(1, assets.Count / favouriteCount);

        for (var i = 0; i < assets.Count && i / step < favouriteCount; i += step)
        {
            assets[i].IsFavorite = true;
        }

        await dbContext.SaveChangesAsync(ct);
    }
}
