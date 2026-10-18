using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>Where the material of a <see cref="VideoSummary"/> comes from.
/// Persisted as int — append, never renumber.</summary>
public enum VideoSummarySource
{
    /// <summary>Everything the user can see captured in one calendar year.</summary>
    Year = 0,

    /// <summary>The contents of one album (manual or smart).</summary>
    Album = 1,
}

/// <summary>Which material goes in, by shape — and so the summary's own
/// frame. Persisted as int — append, never renumber.</summary>
public enum VideoSummaryOrientation
{
    /// <summary>Everything, in a landscape frame; portrait material is fitted
    /// over a blurred fill of itself.</summary>
    All = 0,

    /// <summary>Only landscape material, in a landscape frame.</summary>
    Landscape = 1,

    /// <summary>Only portrait material, in a portrait frame — a summary to
    /// watch on a phone held upright.</summary>
    Portrait = 2,
}

/// <summary>Persisted as int — append, never renumber.</summary>
public enum VideoSummaryStatus
{
    Pending = 0,
    Running = 1,
    Completed = 2,
    Failed = 3,
}

/// <summary>
/// An on-demand request to cut a set of videos (and optionally photos) into
/// one summary video. The result is an ordinary <see cref="Asset"/> in the
/// requester's library (<c>/assets/users/{username}/Recuerdos</c>); this row
/// remembers where it came from, how far rendering got, and which asset it
/// produced — which is also how the rest of the app tells a generated summary
/// apart from a video the user recorded: summaries never feed a summary or a
/// memory of their own.
///
/// One row per (owner, source): asking again for the same year or album
/// re-renders over the previous result instead of piling up copies.
/// </summary>
public class VideoSummary
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid OwnerId { get; set; }
    public User Owner { get; set; } = null!;

    public VideoSummarySource Source { get; set; }

    /// <summary>Set for <see cref="VideoSummarySource.Year"/>.</summary>
    public int? Year { get; set; }

    /// <summary>Set for <see cref="VideoSummarySource.Album"/>. Not a foreign key:
    /// deleting the album must not take the finished video with it.</summary>
    public Guid? AlbumId { get; set; }

    /// <summary>"year:2024" / "album:{id}" — the natural key the request upserts on.</summary>
    [Required]
    [MaxLength(100)]
    public string SourceKey { get; set; } = string.Empty;

    /// <summary>Human title, also the output file's name ("Resumen 2024").</summary>
    [Required]
    [MaxLength(200)]
    public string Title { get; set; } = string.Empty;

    public bool IncludePhotos { get; set; }

    public VideoSummaryOrientation Orientation { get; set; } = VideoSummaryOrientation.All;

    /// <summary>Upper bound for the summary's length, chosen by the user
    /// (1 to 10 minutes). The cut can come out shorter when material runs out.</summary>
    public int DurationSeconds { get; set; } = DefaultDurationSeconds;

    public const int DefaultDurationSeconds = 300;
    public const int MinDurationSeconds = 60;
    public const int MaxDurationSeconds = 600;

    public VideoSummaryStatus Status { get; set; } = VideoSummaryStatus.Pending;

    /// <summary>0..1 while running.</summary>
    public double Progress { get; set; }

    [MaxLength(500)]
    public string? Error { get; set; }

    /// <summary>The rendered video, once there is one.</summary>
    public Guid? AssetId { get; set; }
    public Asset? Asset { get; set; }

    /// <summary>How many source assets made it into the cut.</summary>
    public int ClipCount { get; set; }

    public DateTime RequestedAt { get; set; } = DateTime.UtcNow;
    public DateTime? CompletedAt { get; set; }
}
