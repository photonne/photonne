using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// What a file is, from its extension. Every path that creates an asset
/// (upload, shared-link upload, indexer, scanner) asks here, so these cases
/// are the ones each of them used to get wrong on its own.
/// </summary>
public sealed class MediaFileTypesTests
{
    [Theory]
    // The upload endpoint stored every RAW as a video.
    [InlineData(".dng")]
    [InlineData(".DNG")]
    [InlineData("dng")]
    [InlineData(".cr2")]
    [InlineData(".cr3")]
    [InlineData(".nef")]
    [InlineData(".arw")]
    [InlineData(".raf")]
    [InlineData(".heic")]
    [InlineData(".jpg")]
    public void Images_AreImages(string extension)
    {
        Assert.Equal(AssetType.Image, MediaFileTypes.Classify(extension));
        Assert.True(MediaFileTypes.IsImage(extension));
        Assert.False(MediaFileTypes.IsVideo(extension));
    }

    [Theory]
    [InlineData(".mp4")]
    [InlineData(".MOV")]
    [InlineData("mkv")]
    // The indexer stored these as photos: its list stopped at nine formats.
    [InlineData(".mpg")]
    [InlineData(".mpeg")]
    [InlineData(".3g2")]
    [InlineData(".ogv")]
    [InlineData(".vob")]
    [InlineData("qt")]
    public void Videos_AreVideos(string extension)
    {
        Assert.Equal(AssetType.Video, MediaFileTypes.Classify(extension));
        Assert.True(MediaFileTypes.IsVideo(extension));
        Assert.False(MediaFileTypes.IsImage(extension));
    }

    [Theory]
    [InlineData(".xyz")]
    [InlineData(".pdf")]
    [InlineData("")]
    [InlineData(null)]
    public void UnknownExtension_IsNotSupported_AndIsNeverAVideo(string? extension)
    {
        Assert.False(MediaFileTypes.IsSupported(extension));
        Assert.Equal(AssetType.Image, MediaFileTypes.Classify(extension));
    }

    [Fact]
    public void NoExtension_IsBothAnImageAndAVideo()
    {
        Assert.Empty(MediaFileTypes.ImageExtensions.Intersect(MediaFileTypes.VideoExtensions));
    }

    [Fact]
    public void RawAndHeic_AreTold_FromTheOtherImages()
    {
        Assert.True(MediaFileTypes.IsRaw(".dng"));
        Assert.False(MediaFileTypes.IsHeic(".dng"));
        Assert.True(MediaFileTypes.IsHeic("HEIF"));
        Assert.False(MediaFileTypes.IsRaw(".heic"));
        Assert.False(MediaFileTypes.IsRaw(".jpg"));
        Assert.False(MediaFileTypes.IsHeic(".jpg"));
    }

    [Theory]
    [InlineData("DNG", ".dng")]
    [InlineData(".Dng", ".dng")]
    [InlineData(" .mov ", ".mov")]
    [InlineData("", "")]
    [InlineData(null, "")]
    public void Normalize_GivesLowerCaseWithTheDot(string? extension, string expected)
    {
        Assert.Equal(expected, MediaFileTypes.Normalize(extension));
    }
}
