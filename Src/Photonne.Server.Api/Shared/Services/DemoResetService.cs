using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Data;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Periodically wipes the demo database + assets and re-hydrates them from the
/// seed directory, so visitors don't accumulate junk between sessions.
///
/// Wipe scope:
///   - ALL assets, albums, tags, shares, notifications, settings and folders.
///   - ALL users (including the demo user, which the seeder recreates).
///   - Everything under <c>/data/assets/users/</c> and <c>/data/thumbnails/</c>.
///
/// External libraries and data outside those paths are left untouched.
/// </summary>
public sealed class DemoResetService : BackgroundService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly IOptions<DemoModeOptions> _options;
    private readonly DemoSeederService _seeder;
    private readonly ILogger<DemoResetService> _logger;

    /// <summary>
    /// Updated on every reset cycle; consumed by the <c>demo-info</c> endpoint so
    /// the UI can display an accurate countdown without guessing.
    /// </summary>
    public DateTimeOffset NextResetAt { get; private set; }

    public DemoResetService(
        IServiceProvider serviceProvider,
        IOptions<DemoModeOptions> options,
        DemoSeederService seeder,
        ILogger<DemoResetService> logger)
    {
        _serviceProvider = serviceProvider;
        _options = options;
        _seeder = seeder;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!_options.Value.Enabled)
        {
            _logger.LogDebug("[DEMO-RESET] Demo mode disabled — reset service idle.");
            return;
        }

        var interval = TimeSpan.FromHours(Math.Max(1, _options.Value.ResetIntervalHours));
        NextResetAt = DateTimeOffset.UtcNow.Add(interval);

        _logger.LogInformation(
            "[DEMO-RESET] Running every {Hours}h — next reset at {Next:u}",
            interval.TotalHours, NextResetAt);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await Task.Delay(interval, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                return;
            }

            try
            {
                await ResetAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "[DEMO-RESET] Reset failed — will retry next cycle");
            }

            NextResetAt = DateTimeOffset.UtcNow.Add(interval);
        }
    }

    private async Task ResetAsync(CancellationToken ct)
    {
        _logger.LogInformation("[DEMO-RESET] Starting scheduled reset");

        using (var scope = _serviceProvider.CreateScope())
        {
            var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
            await WipeDatabaseAsync(dbContext, ct);
        }

        WipeFilesystem();

        // Re-seed using the standard seeder path. SeedAsync handles user creation,
        // file copy, indexation, albums and favourites.
        await _seeder.SeedAsync(ct);

        _logger.LogInformation("[DEMO-RESET] Reset complete");
    }

    private async Task WipeDatabaseAsync(ApplicationDbContext dbContext, CancellationToken ct)
    {
        // Order matters: child tables first, then parents. Using TRUNCATE with CASCADE
        // on the parent tables is faster, but we keep it in EF to avoid provider-specific
        // SQL and to respect cascade behaviours declared on the model.
        //
        // Tables intentionally wiped: everything user-generated. We deliberately do NOT
        // touch migration metadata or seed reference data.
        dbContext.AssetThumbnails.RemoveRange(dbContext.AssetThumbnails);
        dbContext.AssetExifs.RemoveRange(dbContext.AssetExifs);
        dbContext.AssetTags.RemoveRange(dbContext.AssetTags);
        dbContext.AssetUserTags.RemoveRange(dbContext.AssetUserTags);
        dbContext.AssetMlJobs.RemoveRange(dbContext.AssetMlJobs);
        dbContext.AlbumAssets.RemoveRange(dbContext.AlbumAssets);
        dbContext.AlbumPermissions.RemoveRange(dbContext.AlbumPermissions);
        dbContext.Albums.RemoveRange(dbContext.Albums);
        dbContext.SharedLinks.RemoveRange(dbContext.SharedLinks);
        dbContext.Notifications.RemoveRange(dbContext.Notifications);
        dbContext.UserTags.RemoveRange(dbContext.UserTags);
        dbContext.Assets.RemoveRange(dbContext.Assets);
        dbContext.FolderPermissions.RemoveRange(dbContext.FolderPermissions);
        dbContext.Folders.RemoveRange(dbContext.Folders);
        dbContext.ExternalLibraryPermissions.RemoveRange(dbContext.ExternalLibraryPermissions);
        dbContext.ExternalLibraries.RemoveRange(dbContext.ExternalLibraries);
        dbContext.Settings.RemoveRange(dbContext.Settings);
        dbContext.RefreshTokens.RemoveRange(dbContext.RefreshTokens);
        dbContext.Users.RemoveRange(dbContext.Users);

        await dbContext.SaveChangesAsync(ct);
        _logger.LogInformation("[DEMO-RESET] Database wiped");
    }

    private void WipeFilesystem()
    {
        // Hard-coded to the well-known internal paths. SettingsService exposes the assets
        // path; thumbnails live at /data/thumbnails by the Dockerfile convention. We only
        // delete contents, never the parent directories themselves — those are mount
        // points in Docker.
        TryDeleteDirectoryContents("/data/assets/users");
        TryDeleteDirectoryContents("/data/thumbnails");
    }

    private void TryDeleteDirectoryContents(string path)
    {
        if (!Directory.Exists(path))
            return;

        foreach (var entry in Directory.EnumerateFileSystemEntries(path))
        {
            try
            {
                if (File.Exists(entry))
                    File.Delete(entry);
                else
                    Directory.Delete(entry, recursive: true);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "[DEMO-RESET] Failed to delete '{Entry}'", entry);
            }
        }
    }
}
