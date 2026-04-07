using System.Collections.Concurrent;
using System.Runtime.CompilerServices;
using System.Threading.Channels;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public record ScanProgressUpdate(
    string Message,
    int Percentage,
    int AssetsFound,
    int AssetsIndexed,
    int AssetsMarkedOffline,
    bool IsCompleted,
    string? Error = null);

public class ExternalLibraryScanService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly DirectoryScanner _scanner;
    private readonly IServiceScopeFactory _scopeFactory;

    public ExternalLibraryScanService(
        ApplicationDbContext dbContext,
        DirectoryScanner scanner,
        IServiceScopeFactory scopeFactory)
    {
        _dbContext = dbContext;
        _scanner = scanner;
        _scopeFactory = scopeFactory;
    }

    public async IAsyncEnumerable<ScanProgressUpdate> ScanAsync(
        Guid libraryId,
        [EnumeratorCancellation] CancellationToken ct)
    {
        var channel = Channel.CreateUnbounded<ScanProgressUpdate>();

        var task = Task.Run(async () =>
        {
            try
            {
                await RunScanAsync(libraryId, channel.Writer, ct);
            }
            catch (Exception ex)
            {
                channel.Writer.TryWrite(new ScanProgressUpdate(
                    $"Unexpected error: {ex.Message}", 100, 0, 0, 0, true, ex.Message));
            }
            finally
            {
                channel.Writer.TryComplete();
            }
        }, ct);

        await foreach (var update in channel.Reader.ReadAllAsync(ct))
            yield return update;

        await task;
    }

    private async Task RunScanAsync(
        Guid libraryId,
        ChannelWriter<ScanProgressUpdate> writer,
        CancellationToken ct)
    {
        var library = await _dbContext.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == libraryId, ct);

        if (library == null)
        {
            await writer.WriteAsync(new ScanProgressUpdate(
                "Library not found.", 0, 0, 0, 0, true, "Library not found."), ct);
            return;
        }

        if (!Directory.Exists(library.Path))
        {
            await writer.WriteAsync(new ScanProgressUpdate(
                $"Directory not found: {library.Path}", 0, 0, 0, 0, true,
                $"Directory does not exist: {library.Path}"), ct);
            return;
        }

        // Mark as running
        library.LastScanStatus = ExternalLibraryScanStatus.Running;
        await _dbContext.SaveChangesAsync(ct);

        await writer.WriteAsync(new ScanProgressUpdate(
            "Scanning directory...", 5, 0, 0, 0, false), ct);

        // Read worker count from the shared TaskSettings.ThumbnailWorkers setting
        // (same setting used by IndexAssetsEndpoint — no new setting needed)
        int workers;
        using (var settingsScope = _scopeFactory.CreateScope())
        {
            var settingsService = settingsScope.ServiceProvider.GetRequiredService<SettingsService>();
            var raw = await settingsService.GetSettingAsync("TaskSettings.ThumbnailWorkers", Guid.Empty, "2");
            workers = Math.Clamp(int.TryParse(raw, out var n) ? n : 2, 1, 16);
        }

        // Discover files
        var scannedFiles = (await _scanner.ScanDirectoryAsync(library.Path, ct)).ToList();

        if (!library.ImportSubfolders)
        {
            var normalizedRoot = library.Path.TrimEnd(Path.DirectorySeparatorChar, '/');
            scannedFiles = scannedFiles
                .Where(f => string.Equals(
                    Path.GetDirectoryName(f.FullPath)?.TrimEnd(Path.DirectorySeparatorChar, '/'),
                    normalizedRoot,
                    StringComparison.OrdinalIgnoreCase))
                .ToList();
        }

        var total = scannedFiles.Count;
        await writer.WriteAsync(new ScanProgressUpdate(
            $"Found {total} files. Indexing with {workers} workers...", 10, total, 0, 0, false), ct);

        // Track existing paths to detect offline files after the scan
        var existingPaths = await _dbContext.Assets
            .Where(a => a.ExternalLibraryId == libraryId && a.DeletedAt == null)
            .Select(a => a.FullPath)
            .ToHashSetAsync(StringComparer.OrdinalIgnoreCase, ct);

        // Thread-safe collections for parallel processing
        var scannedPaths = new ConcurrentBag<string>();
        int indexed = 0;
        int processed = 0;

        // Parallel indexing — each task gets its own DI scope (own DbContext + services)
        await Parallel.ForEachAsync(
            scannedFiles,
            new ParallelOptions { MaxDegreeOfParallelism = workers, CancellationToken = ct },
            async (file, innerCt) =>
            {
                using var scope = _scopeFactory.CreateScope();
                var indexingService = scope.ServiceProvider.GetRequiredService<AssetIndexingService>();

                var normalizedPath = file.FullPath.Replace('\\', '/').TrimEnd('/');
                scannedPaths.Add(normalizedPath);

                await indexingService.IndexFileAsync(file.FullPath, library.OwnerId, innerCt, libraryId);

                Interlocked.Increment(ref indexed);
                var current = Interlocked.Increment(ref processed);

                if (current % 10 == 0 || current == total)
                {
                    var pct = 10 + (int)((double)current / Math.Max(total, 1) * 80);
                    await writer.WriteAsync(new ScanProgressUpdate(
                        $"Indexed {current}/{total}: {file.FileName}",
                        pct, total, indexed, 0, false), innerCt);
                }
            });

        // Mark missing files as offline
        var scannedPathsSet = new HashSet<string>(scannedPaths, StringComparer.OrdinalIgnoreCase);
        var offlinePaths = existingPaths.Except(scannedPathsSet).ToList();
        int markedOffline = 0;

        if (offlinePaths.Count > 0)
        {
            await writer.WriteAsync(new ScanProgressUpdate(
                "Checking for missing files...", 92, total, indexed, 0, false), ct);

            var offlineAssets = await _dbContext.Assets
                .Where(a => a.ExternalLibraryId == libraryId
                         && a.DeletedAt == null
                         && offlinePaths.Contains(a.FullPath))
                .ToListAsync(ct);

            foreach (var asset in offlineAssets)
            {
                asset.IsFileMissing = true;
                markedOffline++;
            }

            await _dbContext.SaveChangesAsync(ct);
        }

        // Update library stats
        library.LastScannedAt = DateTime.UtcNow;
        library.LastScanStatus = ExternalLibraryScanStatus.Completed;
        library.LastScanAssetsFound = total;
        library.LastScanAssetsAdded = scannedPathsSet.Except(existingPaths).Count();
        library.LastScanAssetsRemoved = markedOffline;
        await _dbContext.SaveChangesAsync(ct);

        await writer.WriteAsync(new ScanProgressUpdate(
            $"Scan complete. {indexed} indexed, {markedOffline} marked offline.",
            100, total, indexed, markedOffline, true), ct);
    }
}
