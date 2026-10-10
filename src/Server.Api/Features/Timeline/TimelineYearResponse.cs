namespace Photonne.Server.Api.Features.Timeline;

/// <summary>
/// One year of the compressed yearly view: the total visible-asset count
/// plus a small sample distributed evenly across the year's timeline, newest
/// first. The client renders a fixed number of rows per year from the sample
/// and shows <see cref="Count"/> in the year header, so a 10.000-photo year
/// occupies the same scroll height as a 50-photo one.
/// </summary>
public class TimelineYearResponse
{
    public int Year { get; set; }

    /// <summary>Total visible assets captured in this year.</summary>
    public int Count { get; set; }

    /// <summary>
    /// Evenly-sampled assets (every ⌈Count/sample⌉-th item of the year's
    /// newest-first timeline), newest first. At most the requested sample
    /// size; the whole year when Count is smaller.
    /// </summary>
    public List<TimelineResponse> Items { get; set; } = new();
}
