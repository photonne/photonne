using Photonne.Server.Api.Shared.Services.Video;

namespace Photonne.Server.Api.Tests.Videos;

public sealed class VideoPlaybackPolicyTests
{
    [Theory]
    [InlineData(".mp4", "h264", 1920, 1080, "aac", false)]
    [InlineData(".MOV", "h264", 1080, 1920, null, false)]
    [InlineData(".mov", "hevc", 1920, 1080, "aac", true)]  // iPhone HEVC: not in most desktop browsers
    [InlineData(".mp4", "h264", 3840, 2160, "aac", true)]  // 4K: too much for a phone on mobile data
    [InlineData(".avi", "h264", 640, 480, "mp3", true)]    // container
    [InlineData(".mkv", "vp9", 1280, 720, "opus", true)]
    [InlineData(".mp4", "h264", 1280, 720, "pcm_s16le", true)]
    public void Only_what_doesnt_play_everywhere_needs_a_copy(
        string extension, string codec, int width, int height, string? audio, bool expected)
    {
        Assert.Equal(expected, VideoPlaybackPolicy.NeedsPlaybackCopy(new VideoProbe(extension, codec, width, height, audio)));
    }
}
