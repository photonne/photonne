using Microsoft.Extensions.Configuration;
using Photonne.Server.Api.Shared.Services.Video;

namespace Photonne.Server.Api.Tests.Videos;

public sealed class VideoPlaybackStorageTests
{
    private const long Gb = 1024L * 1024 * 1024;

    private static VideoPlaybackStorage From(params (string Key, string? Value)[] values) =>
        VideoPlaybackStorage.From(new ConfigurationBuilder()
            .AddInMemoryCollection(values.Select(v => new KeyValuePair<string, string?>(v.Key, v.Value)))
            .Build());

    [Fact]
    public void Defaults_to_the_thumbnails_disk_keeping_10_GB_free()
    {
        var storage = From(("ThumbnailsPath", "/data/thumbnails"));
        Assert.Equal(Path.Combine("/data/thumbnails", "playback"), storage.Root);
        Assert.Equal(10 * Gb, storage.MinFreeBytes);
    }

    [Theory]
    [InlineData("/data/playback", "/data/playback")]
    [InlineData("  ", "/data/thumbnails/playback")] // an empty VIDEO_PLAYBACK_PATH from the compose
    public void Reads_the_path(string value, string expected)
    {
        Assert.Equal(expected, From(("ThumbnailsPath", "/data/thumbnails"), ("Video:PlaybackPath", value)).Root);
    }

    [Theory]
    [InlineData("50", 50)]
    [InlineData("0", 0)]
    [InlineData("-5", 0)]
    public void Reads_the_reserve(string value, long expectedGb)
    {
        Assert.Equal(expectedGb * Gb, From(("Video:MinFreeSpaceGb", value)).MinFreeBytes);
    }

    [Theory]
    [InlineData(30, 1, true)]
    [InlineData(10, 0, true)]    // exactly the reserve left
    [InlineData(10.5, 1, false)] // the copy would eat into it
    [InlineData(5, 0, false)]
    public void Leaves_the_reserve_free_after_the_copy(double availableGb, double sourceGb, bool expected)
    {
        var storage = new VideoPlaybackStorage("/x", 10 * Gb);
        Assert.Equal(expected, storage.HasRoom((long)(availableGb * Gb), (long)(sourceGb * Gb)));
    }

    [Fact]
    public void A_zero_reserve_never_stops()
    {
        Assert.True(new VideoPlaybackStorage("/x", 0).HasRoom(0, 100 * Gb));
    }

    [Fact]
    public void Reads_the_free_space_of_the_disk_under_the_folder()
    {
        var root = Path.Combine(Path.GetTempPath(), "photonne-playback-" + Guid.NewGuid().ToString("N"), "nested");
        try
        {
            Assert.True(new VideoPlaybackStorage(root, Gb).AvailableBytes() > 0);
            Assert.True(Directory.Exists(root));
        }
        finally
        {
            Directory.Delete(Path.GetDirectoryName(root)!, recursive: true);
        }
    }
}
