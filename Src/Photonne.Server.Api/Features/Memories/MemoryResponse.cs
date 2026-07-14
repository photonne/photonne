using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories;

/// <summary>One card in the Recuerdos feed. Title and subtitle arrive rendered —
/// the client shows them verbatim rather than rebuilding the wording per
/// platform from <see cref="Kind"/>.</summary>
public class MemoryResponse
{
    public Guid Id { get; set; }

    /// <summary>Enum name ("OnThisDay"), not its number: the wire stays readable
    /// and the client isn't pinned to our numbering.</summary>
    public string Kind { get; set; } = string.Empty;

    public string Title { get; set; } = string.Empty;
    public string? Subtitle { get; set; }

    public Guid? CoverAssetId { get; set; }
    public int AssetCount { get; set; }

    /// <summary>Capture-date span, in the photo's own wall-clock. Same frame as
    /// TimelineResponse.FileCreatedAt.</summary>
    public DateTime WindowStart { get; set; }
    public DateTime WindowEnd { get; set; }
}

/// <summary>A memory plus its assets, for the detail view.</summary>
public class MemoryDetailResponse : MemoryResponse
{
    public List<TimelineResponse> Assets { get; set; } = new();
}
