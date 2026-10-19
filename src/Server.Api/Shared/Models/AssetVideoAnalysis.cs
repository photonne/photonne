using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// A video's second-by-second measurements for picking its best moments
/// (<c>Features.VideoSummaries.Highlights.VideoProfile</c>, as JSON). Cached
/// because analysing means decoding the whole video: a second summary over the
/// same videos, or re-rendering one, reads them from here.
///
/// Valid while <see cref="Checksum"/> matches the asset's and
/// <see cref="AnalyzerVersion"/> the analyzer's; otherwise it's redone.
/// </summary>
public class AssetVideoAnalysis
{
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;

    [Required]
    [MaxLength(128)]
    public string Checksum { get; set; } = string.Empty;

    public int AnalyzerVersion { get; set; }

    /// <summary>The profile as JSON (jsonb).</summary>
    [Required]
    public string Profile { get; set; } = "{}";

    public DateTime AnalyzedAt { get; set; } = DateTime.UtcNow;
}
