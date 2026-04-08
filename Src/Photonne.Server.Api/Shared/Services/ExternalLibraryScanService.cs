using System.Collections.Concurrent;
using System.Runtime.CompilerServices;
using System.Text.Json;
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
    string? Error = null)
{
    public Guid? TaskId { get; init; }
}

public class ExternalLibraryScanService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly DirectoryScanner _scanner;
    private readonly IServiceScopeFactory _scopeFactory;
    private readonly BackgroundTaskManager _taskManager;

    public ExternalLibraryScanService(
        ApplicationDbContext dbContext,
        DirectoryScanner scanner,
        IServiceScopeFactory scopeFactory,
        BackgroundTaskManager taskManager)
    {
        _dbContext = dbContext;
        _scanner = scanner;
        _scopeFactory = scopeFactory;
        _taskManager = taskManager;
    }

    public async IAsyncEnumerable<ScanProgressUpdate> ScanAsync(
        Guid libraryId,
        [EnumeratorCancellation] CancellationToken httpCt)
    {
        var channel = Channel.CreateUnbounded<ScanProgressUpdate>();

        var entry = _taskManager.Register(BackgroundTaskType.LibraryScan,
            new Dictionary<string, string> { ["libraryId"] = libraryId.ToString() });
        var taskCt = entry.Cts.Token;
        void Send(ScanProgressUpdate upd)
        {
            var updWithId = upd with { TaskId = entry.Id };
            channel.Writer.TryWrite(updWithId);
            entry.Push(JsonSerializer.Serialize(updWithId), updWithId.Percentage, updWithId.Message);
        }

        _ = Task.Run(async () =>
        {
            try
            {
                await RunScanAsync(libraryId, Send, taskCt);
                entry.Finish("Completed");
            }
            catch (OperationCanceledException)
            {
                Send(new ScanProgressUpdate("Escaneo cancelado.", 0, 0, 0, 0, true));
                entry.Finish("Cancelled");
            }
            catch (Exception ex)
            {
                Send(new ScanProgressUpdate($"Unexpected error: {ex.Message}", 100, 0, 0, 0, true, ex.Message));
                entry.Finish("Failed");
            }
            finally
            {
                channel.Writer.TryComplete();
            }
        }, taskCt);

        await foreach (var update in channel.Reader.ReadAllAsync(httpCt))
            yield return update;
    }

    private async Task RunScanAsync(
        Guid libraryId,
        Action<ScanProgressUpdate> send,
        CancellationToken ct)
    {
        var library = await _dbContext.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == libraryId, ct);

        if (library == null)
        {
            send(new ScanProgressUpdate("Library not found.", 0, 0, 0, 0, true, "Library not found."));
            return;
        }

        if (!Directory.Exists(library.Path))
        {
            send(new ScanProgressUpdate(
                $"Directory not found: {library.Path}", 0, 0, 0, 0, true,
                $"Directory does not exist: {library.Path}"));
            return;
        }

        // Mark as running
        library.LastScanStatus = ExternalLibraryScanStatus.Running;
        await _dbContext.SaveChangesAsync(ct);

        send(new ScanProgressUpdate("Scanning directory...", 5, 0, 0, 0, false));

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
        send(new ScanProgressUpdate($"Found {total} files. Indexing with {workers} workers...", 10, total, 0, 0, false));

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
                    send(new ScanProgressUpdate($"Indexed {current}/{total}: {file.FileName}", pct, total, indexed, 0, false));
                }
            });

        // Mark missing files as offline
        var scannedPathsSet = new HashSet<string>(scannedPaths, StringComparer.OrdinalIgnoreCase);
        var offlinePaths = existingPaths.Except(scannedPathsSet).ToList();
        int markedOffline = 0;

        if (offlinePaths.Count > 0)
        {
            send(new ScanProgressUpdate("Checking for missing files...", 92, total, indexed, 0, false));

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

        send(new ScanProgressUpdate(
            $"Scan complete. {indexed} indexed, {markedOffline} marked offline.",
            100, total, indexed, markedOffline, true));
    }
}
