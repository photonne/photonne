using Photonne.Server.Api.Features.Timeline;

namespace Photonne.Server.Api.Features.Utilities;

public class UserDuplicateGroupResponse
{
    public string Hash { get; set; } = string.Empty;
    public long TotalSize { get; set; }
    public List<TimelineResponse> Assets { get; set; } = new();
}
