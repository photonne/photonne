using Photonne.Server.Api.Features.VideoSummaries.Rendering;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>Which material each orientation option lets in, and the frame it cuts to.</summary>
public sealed class SummaryOrientationTests
{
    [Theory]
    [InlineData(VideoSummaryOrientation.All, 16.0 / 9, true)]
    [InlineData(VideoSummaryOrientation.All, 9.0 / 16, true)]
    [InlineData(VideoSummaryOrientation.All, null, true)]
    [InlineData(VideoSummaryOrientation.Landscape, 16.0 / 9, true)]
    [InlineData(VideoSummaryOrientation.Landscape, 9.0 / 16, false)]
    [InlineData(VideoSummaryOrientation.Portrait, 9.0 / 16, true)]
    [InlineData(VideoSummaryOrientation.Portrait, 4.0 / 3, false)]
    // Square fits either frame.
    [InlineData(VideoSummaryOrientation.Landscape, 1.0, true)]
    [InlineData(VideoSummaryOrientation.Portrait, 1.0, true)]
    // Unknown shape only goes into "all".
    [InlineData(VideoSummaryOrientation.Landscape, null, false)]
    [InlineData(VideoSummaryOrientation.Portrait, null, false)]
    public void Matches(VideoSummaryOrientation filter, double? ratio, bool expected) =>
        Assert.Equal(expected, SummaryOrientation.Matches(filter, ratio));

    [Fact]
    public void OnlyAPortraitSummary_IsCutInAPortraitFrame()
    {
        Assert.Equal((1080, 1920), SummaryOrientation.Frame(VideoSummaryOrientation.Portrait));
        Assert.Equal((1920, 1080), SummaryOrientation.Frame(VideoSummaryOrientation.Landscape));
        Assert.Equal((1920, 1080), SummaryOrientation.Frame(VideoSummaryOrientation.All));
    }

    [Fact]
    public void PhotoShape_FollowsTheExifQuarterTurns()
    {
        Assert.Equal(4.0 / 3, SummaryOrientation.PhotoAspectRatio(4000, 3000, 1));
        // Orientation 6: stored landscape, shown portrait.
        Assert.Equal(3.0 / 4, SummaryOrientation.PhotoAspectRatio(4000, 3000, 6));
        Assert.Null(SummaryOrientation.PhotoAspectRatio(null, 3000, 1));
    }
}
