using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Photonne.Server.Api.Shared.Services.Video;

namespace Photonne.Server.Api.Tests.Videos;

public sealed class VideoTranscodeOptionsTests
{
    private static VideoTranscodeOptions From(params (string Key, string? Value)[] values) =>
        VideoTranscodeOptions.From(
            new ConfigurationBuilder()
                .AddInMemoryCollection(values.Select(v => new KeyValuePair<string, string?>(v.Key, v.Value)))
                .Build(),
            NullLogger.Instance);

    [Fact]
    public void Defaults_to_libx264_with_the_threads_ffmpeg_picks()
    {
        var options = From();
        Assert.Equal(new VideoTranscodeOptions(VideoEncoder.Software, 0), options);

        var arguments = options.Arguments("/in.mov", "/out.mp4.part", hasAudio: true);
        Assert.Contains("-c:v libx264", arguments);
        Assert.DoesNotContain("-threads", arguments);
        Assert.DoesNotContain("-hwaccel", arguments);
        Assert.Contains("-map 0:a:0 ", arguments);
        Assert.Contains("-c:a aac", arguments);
    }

    [Theory]
    [InlineData("nvenc", VideoEncoder.Nvenc)]
    [InlineData("NVENC", VideoEncoder.Nvenc)]
    [InlineData("software", VideoEncoder.Software)]
    [InlineData("quicksync", VideoEncoder.Software)] // unknown: the CPU, never nothing
    public void Reads_the_encoder(string value, VideoEncoder expected)
    {
        Assert.Equal(expected, From(("Video:Encoder", value)).Encoder);
    }

    [Theory]
    [InlineData("4", 4)]
    [InlineData("0", 0)]
    [InlineData("-2", 0)]
    public void Reads_the_threads(string value, int expected)
    {
        Assert.Equal(expected, From(("Video:Threads", value)).Threads);
    }

    [Fact]
    public void Nvenc_decodes_on_the_gpu_and_caps_every_stage_threads()
    {
        var arguments = new VideoTranscodeOptions(VideoEncoder.Nvenc, 2).Arguments("/in.mov", "/out.mp4.part", hasAudio: false);

        Assert.StartsWith("-filter_threads 2 -threads 2 -hwaccel cuda -i \"/in.mov\" ", arguments);
        Assert.Contains("-c:v h264_nvenc", arguments);
        Assert.DoesNotContain("libx264", arguments);
        Assert.Contains("-profile:v high -threads 2 -an ", arguments);
        Assert.EndsWith("-f mp4 \"/out.mp4.part\"", arguments);
    }
}
