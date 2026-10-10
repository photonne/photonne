using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Persists a run's drafts for one user. This is where the table earns its
/// keep: an existing memory is refreshed in place rather than re-created, so
/// its identity survives the nightly run.
/// </summary>
internal sealed class MemoryWriter
{
    private readonly ApplicationDbContext _db;

    public MemoryWriter(ApplicationDbContext db) => _db = db;

    /// <summary>
    /// Upserts <paramref name="drafts"/> on (OwnerId, DedupeKey) and deletes the
    /// user's memories that this run no longer produced.
    /// </summary>
    /// <returns>(created, updated, removed)</returns>
    public async Task<(int Created, int Updated, int Removed)> WriteAsync(
        Guid userId,
        IReadOnlyList<MemoryDraft> drafts,
        DateTime localToday,
        CancellationToken ct)
    {
        var now = DateTime.UtcNow;
        var keys = drafts.Select(d => d.DedupeKey).ToHashSet();

        var existing = await _db.Memories
            .Include(m => m.Assets)
            .Where(m => m.OwnerId == userId)
            .ToDictionaryAsync(m => m.DedupeKey, ct);

        int created = 0, updated = 0;

        foreach (var draft in drafts)
        {
            var score = MemoryScoring.Score(draft, localToday);

            if (existing.TryGetValue(draft.DedupeKey, out var row))
            {
                // Id and FirstGeneratedAt deliberately untouched: the client keys
                // its "new" badge and its dismiss state off them, and a memory
                // that reappears every night is the same memory, not a new one.
                row.Title = draft.Title;
                row.Subtitle = draft.Subtitle;
                // Refreshed here, not just on insert: this is what backfills the
                // grouping onto rows that predate it — one run and every
                // feed-visible memory has its row, with no migration script.
                row.ThemeKey = draft.ThemeKey;
                row.GroupTitle = draft.GroupTitle;
                row.CardLabel = draft.CardLabel;
                row.CoverAssetId = draft.CoverAssetId;
                row.WindowStart = draft.WindowStart;
                row.WindowEnd = draft.WindowEnd;
                row.AssetCount = draft.AssetIds.Count;
                row.Score = score;
                row.LastGeneratedAt = now;

                _db.MemoryAssets.RemoveRange(row.Assets);
                row.Assets = BuildAssets(row.Id, draft);
                updated++;
            }
            else
            {
                var memory = new Memory
                {
                    OwnerId = userId,
                    Kind = draft.Kind,
                    Title = draft.Title,
                    Subtitle = draft.Subtitle,
                    ThemeKey = draft.ThemeKey,
                    GroupTitle = draft.GroupTitle,
                    CardLabel = draft.CardLabel,
                    CoverAssetId = draft.CoverAssetId,
                    WindowStart = draft.WindowStart,
                    WindowEnd = draft.WindowEnd,
                    AssetCount = draft.AssetIds.Count,
                    Score = score,
                    DedupeKey = draft.DedupeKey,
                    FirstGeneratedAt = now,
                    LastGeneratedAt = now,
                };
                memory.Assets = BuildAssets(memory.Id, draft);
                _db.Memories.Add(memory);
                created++;
            }
        }

        // A memory whose source photos were deleted (or that dropped under its
        // generator's threshold) must disappear, or the feed accumulates rows
        // pointing at nothing. Dismissed rows are kept: re-creating one the user
        // already waved away would resurrect it.
        var stale = existing.Values
            .Where(m => !keys.Contains(m.DedupeKey) && !m.IsDismissed)
            .ToList();
        _db.Memories.RemoveRange(stale);

        await _db.SaveChangesAsync(ct);
        return (created, updated, stale.Count);
    }

    private static List<MemoryAsset> BuildAssets(Guid memoryId, MemoryDraft draft) =>
        draft.AssetIds
            .Select((id, index) => new MemoryAsset
            {
                MemoryId = memoryId,
                AssetId = id,
                Position = index,
            })
            .ToList();
}
