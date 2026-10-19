using System.Text.Json;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>
/// A chosen cut, as stored on <see cref="Shared.Models.VideoSummary.Plan"/>:
/// the clips in the order they play, and every asset they were chosen from,
/// so the user can swap a clip for another moment without the server
/// resolving the year or the album again.
/// </summary>
public sealed record SummaryPlanDocument(List<SummaryClip> Clips, List<SummaryCandidate> Candidates)
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    /// <summary>The candidates without their embeddings: those are only for
    /// choosing, and would make the document a hundred times bigger.</summary>
    public static SummaryPlanDocument Of(IEnumerable<SummaryClip> clips, IEnumerable<SummaryCandidate> candidates) =>
        new(
            clips.ToList(),
            candidates
                .Select(c => c with { Embedding = null })
                .OrderBy(c => c.CapturedAt)
                .ThenBy(c => c.AssetId)
                .ToList());

    public string Serialize() => JsonSerializer.Serialize(this, Json);

    public static SummaryPlanDocument? Deserialize(string? json)
    {
        if (string.IsNullOrWhiteSpace(json)) return null;
        try
        {
            return JsonSerializer.Deserialize<SummaryPlanDocument>(json, Json);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    public double TotalSeconds => Clips.Sum(c => c.Length);
}
