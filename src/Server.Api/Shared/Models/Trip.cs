using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// A run of photos taken away from home, over more than one day.
///
/// An entity of its own rather than just a <see cref="Memory"/> of
/// <see cref="MemoryKind.Trip"/>: re-deriving trips from scratch every night
/// would make their names and boundaries flicker as photos are added, and a trip
/// is independently useful to the map and the timeline later. The memory points
/// at this row; this row is the fact.
/// </summary>
public class Trip
{
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>
    /// Trips are personal. Home is a per-user notion — your holiday is someone
    /// else's ordinary week — so a trip is derived from its owner's own photos,
    /// not from everything they can see.
    /// </summary>
    public Guid OwnerId { get; set; }
    public User Owner { get; set; } = null!;

    /// <summary>Built from the places visited, in Spanish, by TripNaming.</summary>
    [Required]
    [MaxLength(200)]
    public string Title { get; set; } = string.Empty;

    /// <summary>Capture-date span, in the photo's own naive local wall-clock —
    /// same frame as <see cref="Asset.CapturedAt"/>.</summary>
    public DateTime WindowStart { get; set; }
    public DateTime WindowEnd { get; set; }

    /// <summary>Mean position of the trip's photos. Used to measure the distance
    /// from home and to keep the dedupe key stable.</summary>
    public double CentroidLat { get; set; }
    public double CentroidLon { get; set; }

    public int AssetCount { get; set; }

    /// <summary>
    /// Stable natural key. Built from the start date and a coarse centroid cell,
    /// so that a trip which grows by a day — or gets renamed because you geocoded
    /// more of its photos — updates in place rather than appearing twice.
    /// </summary>
    [Required]
    [MaxLength(200)]
    public string DedupeKey { get; set; } = string.Empty;

    public DateTime DetectedAt { get; set; } = DateTime.UtcNow;

    public ICollection<TripPlace> Places { get; set; } = new List<TripPlace>();
}

/// <summary>How many of a trip's photos were taken at a given place. Drives the
/// trip's title: the place you shot most is the place you went.</summary>
public class TripPlace
{
    public Guid TripId { get; set; }
    public Trip Trip { get; set; } = null!;

    public Guid PlaceId { get; set; }
    public Place Place { get; set; } = null!;

    public int AssetCount { get; set; }
}
