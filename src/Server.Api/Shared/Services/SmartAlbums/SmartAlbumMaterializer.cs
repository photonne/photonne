using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.SmartAlbums;

/// <summary>
/// Writes a smart album's rule result as AlbumAsset rows, so the album is a set
/// of photos like any other: sharing it grants read on its members through the
/// same AlbumPermission → AlbumAsset path a manual album uses (visibility,
/// thumbnails, search), and reading it is a plain query on its rows.
///
/// A pass diffs the rule (resolved as the owner sees it) against the stored
/// rows: inserts what newly matches, deletes what no longer does. It runs when
/// the rule is saved, when someone opens the album, and on
/// <see cref="SmartAlbumMaterializationWorker"/>'s periodic sweep — which also
/// backfills albums created before membership was materialized.
/// </summary>
public sealed class SmartAlbumMaterializer
{
    private static readonly JsonSerializerOptions RuleJson = new(JsonSerializerDefaults.Web);

    // One pass per album at a time. A caller that finds a pass running waits
    // for it and, if that pass started after the caller asked, reuses it: a
    // burst of viewers opening the album costs one resolve, not one each.
    private static readonly ConcurrentDictionary<Guid, AlbumGate> Gates = new();

    private sealed class AlbumGate
    {
        public readonly SemaphoreSlim Lock = new(1, 1);
        public long LastPassStarted;
    }

    private readonly ApplicationDbContext _db;
    private readonly SmartAlbumResolver _resolver;
    private readonly IMemoryCache _cache;
    private readonly ILogger<SmartAlbumMaterializer> _logger;

    public SmartAlbumMaterializer(
        ApplicationDbContext db,
        SmartAlbumResolver resolver,
        IMemoryCache cache,
        ILogger<SmartAlbumMaterializer> logger)
    {
        _db = db;
        _resolver = resolver;
        _cache = cache;
        _logger = logger;
    }

    /// <summary>
    /// Brings the album's rows in line with its rule. A no-op for manual
    /// albums. An invalid rule empties the album rather than failing the caller.
    /// </summary>
    public async Task MaterializeAsync(Guid albumId, CancellationToken ct)
    {
        var requested = Stopwatch.GetTimestamp();
        var gate = Gates.GetOrAdd(albumId, _ => new AlbumGate());

        await gate.Lock.WaitAsync(ct);
        try
        {
            if (Interlocked.Read(ref gate.LastPassStarted) > requested) return;
            Interlocked.Exchange(ref gate.LastPassStarted, Stopwatch.GetTimestamp());
            await RunPassAsync(albumId, ct);
        }
        finally
        {
            gate.Lock.Release();
        }
    }

    /// <summary>Every smart album, one at a time; a failing album doesn't stop the rest.</summary>
    public async Task MaterializeAllAsync(CancellationToken ct)
    {
        var ids = await _db.Albums.AsNoTracking()
            .Where(a => a.Kind == AlbumKind.Smart)
            .Select(a => a.Id)
            .ToListAsync(ct);

        foreach (var id in ids)
        {
            try
            {
                await MaterializeAsync(id, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Smart album {AlbumId} could not be materialized", id);
                _db.ChangeTracker.Clear();
            }
        }
    }

    private async Task RunPassAsync(Guid albumId, CancellationToken ct)
    {
        var album = await _db.Albums
            .FirstOrDefaultAsync(a => a.Id == albumId && a.Kind == AlbumKind.Smart, ct);
        if (album == null) return;

        var matches = await ResolveAsync(album, ct);
        var matchIds = matches.Select(a => a.Id);

        var removed = await _db.AlbumAssets
            .Where(aa => aa.AlbumId == albumId && !matchIds.Contains(aa.AssetId))
            .ExecuteDeleteAsync(ct);

        var added = await matches
            .Where(a => !_db.AlbumAssets.Any(aa => aa.AlbumId == albumId && aa.AssetId == a.Id))
            .Select(a => a.Id)
            .ToListAsync(ct);

        // Smart albums are read newest first, never by Order.
        var now = DateTime.UtcNow;
        _db.AlbumAssets.AddRange(added.Select(id => new AlbumAsset { AlbumId = albumId, AssetId = id, AddedAt = now }));

        // A chosen cover that left the album falls back to the newest member.
        if (removed > 0 && album.CoverAssetId is { } coverId && !await matchIds.ContainsAsync(coverId, ct))
            album.CoverAssetId = null;

        album.ResolveMode = SmartResolveMode.Materialized;
        album.LastMaterializedAt = now;
        await _db.SaveChangesAsync(ct);

        if (removed > 0 || added.Count > 0)
        {
            _logger.LogInformation("Smart album {AlbumId} materialized: +{Added} -{Removed}", albumId, added.Count, removed);
            await InvalidateAlbumListsAsync(album, ct);
        }
    }

    private async Task<IQueryable<Asset>> ResolveAsync(Album album, CancellationToken ct)
    {
        var rule = album.SmartRule is null ? null : JsonSerializer.Deserialize<SmartRuleNode>(album.SmartRule, RuleJson);
        if (rule == null) return _db.Assets.Where(_ => false);

        try
        {
            return await _resolver.ResolveAsync(rule, album.OwnerId, ct);
        }
        catch (SmartRuleException ex)
        {
            _logger.LogWarning("Smart album {AlbumId} has an invalid rule: {Message}", album.Id, ex.Message);
            return _db.Assets.Where(_ => false);
        }
    }

    // Count and cover live in every member's cached album list.
    private async Task InvalidateAlbumListsAsync(Album album, CancellationToken ct)
    {
        var members = await _db.AlbumPermissions.AsNoTracking()
            .Where(p => p.AlbumId == album.Id)
            .Select(p => p.UserId)
            .ToListAsync(ct);

        _cache.Remove($"albums:{album.OwnerId}");
        foreach (var userId in members)
            _cache.Remove($"albums:{userId}");
    }
}
