using System.Text;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Client.Web.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Maintenance;

/// <summary>
/// Snapshot persisted by the indexing-coverage task (Settings key
/// <see cref="MaintenanceService.IndexingCoverageResultKey"/>). The disk walk
/// can take minutes on a big library, so the stats screen reads this stored
/// result instead of recomputing per request.
/// </summary>
public sealed class IndexingCoverageResult
{
    public DateTime VerifiedAtUtc { get; set; }
    public int TotalFiles { get; set; }
    public int Indexed { get; set; }
    public int Unsupported { get; set; }
    public int Unindexed { get; set; }
    public List<string> UnindexedPaths { get; set; } = new();
    public bool UnindexedTruncated { get; set; }
    /// <summary>External libraries whose root was unreachable during the walk —
    /// their files were NOT verified, so a zero-unindexed result with offline
    /// libraries is not full coverage.</summary>
    public int OfflineLibraries { get; set; }
}

public partial class MaintenanceService
{
    public const string IndexingCoverageResultKey = "IndexingCoverage.LastResult";

    // Persisting every path of a massively out-of-sync library would bloat the
    // Settings row; the counters stay exact, only the list is capped.
    private const int MaxPersistedUnindexedPaths = 500;

    /// <summary>
    /// Walks every root with the EXACT discovery criteria the indexer uses
    /// (same <see cref="DirectoryScanner"/>: extensions, hidden/system skip,
    /// trash exclusion) and classifies each physical file as indexed,
    /// unsupported or unindexed. "0 unindexed" therefore means literally
    /// "the indexer has nothing left to pick up".
    /// </summary>
    public async Task<MaintenanceTaskResult> ComputeIndexingCoverageAsync(
        Action<MaintenanceProgressUpdate>? onProgress,
        CancellationToken ct)
    {
        Report(onProgress, "Escaneando la biblioteca gestionada…", 0, 0, 0);

        var libraries = await _dbContext.ExternalLibraries
            .AsNoTracking()
            .Select(l => new { l.Id, l.Path, l.ImportSubfolders })
            .ToListAsync(ct);

        var totalRoots = 1 + libraries.Count;
        int totalFiles = 0, totalAllowed = 0, unsupported = 0, offlineLibraries = 0;
        var unindexed = new List<string>();
        var processed = 0;

        // ── Managed library ─────────────────────────────────────────────────
        var managedScan = await _scanner.ScanDirectoryWithUnsupportedAsync(
            _settingsService.GetAssetsPath(), ct);
        var managedDbPaths = await _dbContext.Assets
            .Where(a => a.DeletedAt == null && a.ExternalLibraryId == null)
            .Select(a => a.FullPath)
            .ToHashSetAsync(StringComparer.OrdinalIgnoreCase, ct);

        totalFiles += managedScan.Allowed.Count + managedScan.Unsupported.Count;
        totalAllowed += managedScan.Allowed.Count;
        unsupported += managedScan.Unsupported.Count;

        foreach (var file in managedScan.Allowed)
        {
            ct.ThrowIfCancellationRequested();
            // Same disk→DB path mapping the indexer stores (AssetIndexingService).
            var stored = NormalizeVirtualPath(await _settingsService.VirtualizePathAsync(file.FullPath));
            if (!ContainsWithUnicodeFallback(managedDbPaths, stored))
                unindexed.Add(stored);

            processed++;
            if (processed % 500 == 0)
                Report(onProgress, $"Biblioteca gestionada: {processed} ficheros comprobados…",
                    totalRoots > 0 ? 80.0 * processed / Math.Max(1, totalAllowed) / totalRoots : 0,
                    processed, unindexed.Count);
        }

        // ── External libraries ──────────────────────────────────────────────
        var rootIndex = 1;
        foreach (var library in libraries)
        {
            ct.ThrowIfCancellationRequested();
            rootIndex++;

            if (!Directory.Exists(library.Path))
            {
                // Unreachable root (unmounted share): its files can't be
                // verified. Counted separately instead of pretending coverage.
                offlineLibraries++;
                continue;
            }

            Report(onProgress, $"Escaneando biblioteca externa: {library.Path}…",
                80.0 * rootIndex / totalRoots, processed, unindexed.Count);

            var scan = await _scanner.ScanDirectoryWithUnsupportedAsync(library.Path, ct);
            var allowed = scan.Allowed.ToList();
            var libraryUnsupported = scan.Unsupported.Count;

            if (!library.ImportSubfolders)
            {
                // Mirror ExternalLibraryScanService: a non-recursive library
                // only imports files sitting directly at its root.
                var normalizedRoot = library.Path.TrimEnd(Path.DirectorySeparatorChar, '/');
                allowed = allowed.Where(f => string.Equals(
                    Path.GetDirectoryName(f.FullPath)?.TrimEnd(Path.DirectorySeparatorChar, '/'),
                    normalizedRoot,
                    StringComparison.OrdinalIgnoreCase)).ToList();
                libraryUnsupported = 0; // out-of-root files are not the library's problem
            }

            var libraryDbPaths = await _dbContext.Assets
                .Where(a => a.DeletedAt == null && a.ExternalLibraryId == library.Id)
                .Select(a => a.FullPath)
                .ToHashSetAsync(StringComparer.OrdinalIgnoreCase, ct);

            totalFiles += allowed.Count + libraryUnsupported;
            totalAllowed += allowed.Count;
            unsupported += libraryUnsupported;

            foreach (var file in allowed)
            {
                ct.ThrowIfCancellationRequested();
                // External assets store the normalized physical path, unvirtualized.
                var stored = NormalizeVirtualPath(file.FullPath);
                if (!ContainsWithUnicodeFallback(libraryDbPaths, stored))
                    unindexed.Add(stored);
                processed++;
            }
        }

        // ── Race re-check ───────────────────────────────────────────────────
        // Files that landed and got indexed DURING the walk would show up as
        // transient false positives; re-query only the candidates.
        if (unindexed.Count > 0)
        {
            Report(onProgress, "Re-comprobando candidatos contra la base de datos…", 85, processed, unindexed.Count);
            var confirmed = new List<string>(unindexed.Count);
            foreach (var chunk in unindexed.Chunk(500))
            {
                ct.ThrowIfCancellationRequested();
                var present = await _dbContext.Assets
                    .Where(a => a.DeletedAt == null && chunk.Contains(a.FullPath))
                    .Select(a => a.FullPath)
                    .ToListAsync(ct);
                var presentSet = present.ToHashSet(StringComparer.OrdinalIgnoreCase);
                confirmed.AddRange(chunk.Where(p => !presentSet.Contains(p)));
            }
            unindexed = confirmed;
        }

        var indexed = totalAllowed - unindexed.Count;

        var result = new IndexingCoverageResult
        {
            VerifiedAtUtc = DateTime.UtcNow,
            TotalFiles = totalFiles,
            Indexed = indexed,
            Unsupported = unsupported,
            Unindexed = unindexed.Count,
            UnindexedPaths = unindexed.Take(MaxPersistedUnindexedPaths).ToList(),
            UnindexedTruncated = unindexed.Count > MaxPersistedUnindexedPaths,
            OfflineLibraries = offlineLibraries,
        };
        await _settingsService.SetSettingAsync(
            IndexingCoverageResultKey, JsonSerializer.Serialize(result), Guid.Empty);

        var message = new StringBuilder(
            $"{totalFiles} ficheros en disco: {indexed} indexados, {unsupported} no soportados, {unindexed.Count} sin indexar.");
        if (offlineLibraries > 0)
            message.Append($" {offlineLibraries} biblioteca(s) externa(s) no disponibles quedaron sin verificar.");

        Report(onProgress, message.ToString(), 100, processed, unindexed.Count);
        return new MaintenanceTaskResult
        {
            Success = true,
            Message = message.ToString(),
            Processed = totalFiles,
            Affected = unindexed.Count,
        };
    }

    /// <summary>Reads the last persisted coverage snapshot, or null when the
    /// task never ran.</summary>
    public async Task<IndexingCoverageResult?> GetLastIndexingCoverageAsync(CancellationToken ct)
    {
        _ = ct;
        var raw = await _settingsService.GetSettingAsync(IndexingCoverageResultKey, Guid.Empty, "");
        if (string.IsNullOrWhiteSpace(raw)) return null;
        try { return JsonSerializer.Deserialize<IndexingCoverageResult>(raw); }
        catch (JsonException) { return null; }
    }

    /// <summary>Membership check tolerant to Unicode normalization mismatches:
    /// the filesystem may hand back NFD names (macOS/SMB) while the DB stores
    /// NFC or vice versa, and neither comparer bridges that.</summary>
    private static bool ContainsWithUnicodeFallback(HashSet<string> paths, string stored)
    {
        if (paths.Contains(stored)) return true;
        var nfc = stored.Normalize(NormalizationForm.FormC);
        if (nfc != stored && paths.Contains(nfc)) return true;
        var nfd = stored.Normalize(NormalizationForm.FormD);
        return nfd != stored && paths.Contains(nfd);
    }

    private static string NormalizeVirtualPath(string path) =>
        path.Replace('\\', '/').TrimEnd('/');
}
