using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// What kind of story a <see cref="Memory"/> tells. The kind drives the title
/// template, the generator that produces it, and its weight in the feed score.
/// Values are persisted — append, never renumber.
/// </summary>
public enum MemoryKind
{
    /// <summary>Same calendar day, previous years — the timeline strip's kind.</summary>
    OnThisDay = 0,

    /// <summary>Same calendar month, a previous year.</summary>
    ThisMonth = 1,

    /// <summary>One named person across several years.</summary>
    PersonThroughYears = 10,

    /// <summary>Two named people who appear together often.</summary>
    PeopleTogether = 11,

    /// <summary>A year's favourites — the only human-curated quality signal we have.</summary>
    FavoritesOfYear = 20,

    /// <summary>A curated Places365 scene ("días de playa").</summary>
    CuratedScene = 30,

    /// <summary>A curated COCO object group (pets, cakes).</summary>
    PetsAndFood = 31,

    /// <summary>Time+place cluster away from home.</summary>
    Trip = 40,
}

/// <summary>
/// A generated story over a set of assets, precomputed nightly rather than
/// resolved per request so it can be ranked, deduped and kept stable from one
/// day to the next.
///
/// Every row belongs to exactly one user. That is not a scoping convenience:
/// face identity is per-user (<see cref="UserFaceAssignment"/>), so "Martina a
/// lo largo de los años" only exists for the user who named Martina. It also
/// makes the read-time gate a single OwnerId equality instead of re-running
/// <see cref="Authorization.AssetVisibilityService"/> on every request.
/// </summary>
public class Memory
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid OwnerId { get; set; }
    public User Owner { get; set; } = null!;

    public MemoryKind Kind { get; set; }

    /// <summary>Rendered server-side, in the user's language. The client shows it verbatim.</summary>
    [Required]
    [MaxLength(200)]
    public string Title { get; set; } = string.Empty;

    [MaxLength(200)]
    public string? Subtitle { get; set; }

    public Guid? CoverAssetId { get; set; }
    public Asset? CoverAsset { get; set; }

    /// <summary>
    /// Capture-date span covered, in the photo's own wall-clock — the same naive
    /// local frame as <see cref="Asset.CapturedAt"/>. Never compare these to
    /// DateTime.UtcNow.
    /// </summary>
    public DateTime WindowStart { get; set; }
    public DateTime WindowEnd { get; set; }

    public int AssetCount { get; set; }

    /// <summary>Feed rank, 0..1. See MemoryScoring.</summary>
    public double Score { get; set; }

    /// <summary>
    /// Stable natural key for this memory within its owner ("onthisday:2019-07-14",
    /// "favyear:2023"). The generator upserts on (OwnerId, DedupeKey) so a nightly
    /// re-run refreshes the row in place instead of producing a duplicate — which
    /// is the entire reason memories live in a table rather than being resolved
    /// per request.
    /// </summary>
    [Required]
    [MaxLength(200)]
    public string DedupeKey { get; set; } = string.Empty;

    /// <summary>First time this memory ever appeared. Survives regeneration, so
    /// the client can tell a genuinely new memory from a refreshed one.</summary>
    public DateTime FirstGeneratedAt { get; set; } = DateTime.UtcNow;

    public DateTime LastGeneratedAt { get; set; } = DateTime.UtcNow;

    /// <summary>Dismissed by the user; excluded from the feed but kept so the
    /// generator doesn't resurrect it on the next run.</summary>
    public bool IsDismissed { get; set; }

    public ICollection<MemoryAsset> Assets { get; set; } = new List<MemoryAsset>();
}

/// <summary>Ordered membership of a <see cref="Memory"/>. Replaced wholesale on
/// every regeneration — no user state lives here.</summary>
public class MemoryAsset
{
    public Guid MemoryId { get; set; }
    public Memory Memory { get; set; } = null!;

    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    /// <summary>Display order within the memory, 0-based.</summary>
    public int Position { get; set; }
}
