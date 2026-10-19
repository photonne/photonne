using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// What a video looks and sounds like, second by second: the raw material the
/// summary planner scores to find a video's best moments and where to cut
/// them. Measured once with ffmpeg at low resolution and kept, so cutting
/// another summary from the same videos doesn't measure them again.
///
/// Stale when the file changes (<see cref="Checksum"/>) or when the measuring
/// itself changes (<see cref="Version"/>): the generator measures again and
/// overwrites the row.
/// </summary>
public class AssetVideoAnalysis
{
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    /// <summary>The asset's checksum when it was measured.</summary>
    [Required]
    [MaxLength(64)]
    public string Checksum { get; set; } = string.Empty;

    public int Version { get; set; }

    public double DurationSeconds { get; set; }

    /// <summary>Mean luma change between frames, per second (0-255 scale).</summary>
    public float[] Motion { get; set; } = [];

    /// <summary>Mean luma, per second (0-255): what is black.</summary>
    public float[] Brightness { get; set; } = [];

    /// <summary>ffmpeg's blur estimate, per second: higher is blurrier.</summary>
    public float[] Blur { get; set; } = [];

    /// <summary>Loudest momentary loudness (LUFS), per second; empty without audio.</summary>
    public float[] Loudness { get; set; } = [];

    /// <summary>Seconds into the video where the shot changes.</summary>
    public float[] SceneCuts { get; set; } = [];

    public DateTime AnalyzedAt { get; set; } = DateTime.UtcNow;
}
