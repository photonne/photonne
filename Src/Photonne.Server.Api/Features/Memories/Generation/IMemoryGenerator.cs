using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Everything a generator is allowed to know. <see cref="Scope"/> is already
/// gated (visibility + the timeline's base predicate), so a generator only ever
/// adds its own condition on top and never reasons about permissions.
/// </summary>
public sealed class MemoryContext
{
    public required Guid UserId { get; init; }

    /// <summary>Assets this user may see, minus deleted/archived/missing/motion
    /// parts. Already AsNoTracking. Compose on top; never widen.</summary>
    public required IQueryable<Asset> Scope { get; init; }

    /// <summary>
    /// Today in the server's configured metadata timezone — the same frame
    /// <see cref="Asset.CapturedAt"/> is stored in. Generators MUST derive every
    /// date bound from this and never touch DateTime.UtcNow: capture dates are
    /// naive local wall-clock, so UtcNow lands on the wrong day for the first
    /// offset-hours of every local day.
    /// </summary>
    public required DateTime LocalToday { get; init; }

    public required ApplicationDbContext Db { get; init; }
}

/// <summary>
/// A memory a generator wants to exist. The writer turns this into a
/// <see cref="Memory"/> row, upserting on <see cref="DedupeKey"/>.
/// </summary>
public sealed class MemoryDraft
{
    public required MemoryKind Kind { get; init; }

    /// <summary>Stable across runs for the same conceptual memory — this is what
    /// stops a nightly re-run from duplicating rows. Include enough to be unique
    /// within the owner, nothing that changes when the asset set grows.</summary>
    public required string DedupeKey { get; init; }

    public required string Title { get; init; }
    public string? Subtitle { get; init; }

    /// <summary>Ordered; index 0 becomes the cover. Must be non-empty.</summary>
    public required IReadOnlyList<Guid> AssetIds { get; init; }

    public required Guid CoverAssetId { get; init; }
    public required DateTime WindowStart { get; init; }
    public required DateTime WindowEnd { get; init; }

    /// <summary>Share of the set the user marked favourite, 0..1. Feeds the score.</summary>
    public double FavoriteRatio { get; init; }

    /// <summary>Share of the set containing a face the user has named, 0..1.</summary>
    public double NamedFaceRatio { get; init; }
}

/// <summary>
/// Produces one kind of memory for one user. Implementations are registered in
/// DI and run in sequence by <see cref="MemoryGenerationService"/>; each one owns
/// its thresholds and its titles.
/// </summary>
public interface IMemoryGenerator
{
    MemoryKind Kind { get; }

    Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct);
}
