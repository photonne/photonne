using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Memories.Generation;

public sealed record MemoryGenerationResult(int Created, int Updated, int Removed)
{
    public static readonly MemoryGenerationResult Empty = new(0, 0, 0);
    public int Total => Created + Updated;
}

/// <summary>
/// Runs every <see cref="IMemoryGenerator"/> for one user and persists the
/// result. Per-user by construction, not by choice: face identity is private
/// (<see cref="UserFaceAssignment"/>), so two people looking at the same shared
/// folder genuinely have different memories over the same photos.
/// </summary>
public sealed class MemoryGenerationService
{
    private readonly ApplicationDbContext _db;
    private readonly AssetVisibilityService _visibility;
    private readonly SettingsService _settings;
    private readonly IEnumerable<IMemoryGenerator> _generators;

    public MemoryGenerationService(
        ApplicationDbContext db,
        AssetVisibilityService visibility,
        SettingsService settings,
        IEnumerable<IMemoryGenerator> generators)
    {
        _db = db;
        _visibility = visibility;
        _settings = settings;
        _generators = generators;
    }

    public async Task<MemoryGenerationResult> RunForUserAsync(Guid userId, CancellationToken ct)
    {
        // Same frame as /api/assets/memories: capture dates are the photo's own
        // wall-clock, so "today" is the configured metadata timezone's today.
        var tz = await MetadataTimeZone.ResolveAsync(_settings, ct);
        var localToday = MetadataTimeZone.LocalNow(tz);

        var scope = await _visibility.GetScopeAsync(userId, ct);

        // The timeline's base gate, mirrored from SmartAlbumResolver: a memory is
        // a timeline item with a story attached, so anything the timeline hides
        // must stay hidden here too.
        var visible = _db.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                     && !a.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart))
            .Where(scope.AssetPredicate());

        var ctx = new MemoryContext
        {
            UserId = userId,
            Scope = visible,
            LocalToday = localToday,
            Db = _db,
        };

        var drafts = new List<MemoryDraft>();
        foreach (var generator in _generators)
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                drafts.AddRange(await generator.GenerateAsync(ctx, ct));
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                // One broken generator must not cost the user every other memory.
                Console.WriteLine($"[MEMORIES] Generator {generator.Kind} failed for user {userId}: {ex.Message}");
            }
        }

        // Two generators claiming the same key would make the upsert write the
        // same row twice in one run; last one wins, deterministically.
        var deduped = drafts
            .GroupBy(d => d.DedupeKey)
            .Select(g => g.Last())
            .ToList();

        var writer = new MemoryWriter(_db);
        var (created, updated, removed) = await writer.WriteAsync(userId, deduped, localToday, ct);
        return new MemoryGenerationResult(created, updated, removed);
    }
}
