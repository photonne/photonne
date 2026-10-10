using Photonne.Server.Api.Features.Assets;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Assets;

/// <summary>
/// The rules behind "original or JPG?" when downloading a RAW or a HEIC/HEIF,
/// and what the app is told before it asks. Pure functions, no database.
/// </summary>
public sealed class AssetDownloadFormatTests
{
    [Theory]
    [InlineData(null, AssetDownloadFormat.Default)]
    [InlineData("", AssetDownloadFormat.Default)]
    [InlineData("original", AssetDownloadFormat.Original)]
    [InlineData("ORIGINAL", AssetDownloadFormat.Original)]
    [InlineData("jpeg", AssetDownloadFormat.Jpeg)]
    [InlineData("jpg", AssetDownloadFormat.Jpeg)]
    public void Parses_TheFormatsTheApiKnows(string? value, AssetDownloadFormat expected)
    {
        Assert.True(AssetDownloadFormats.TryParse(value, out var format));
        Assert.Equal(expected, format);
    }

    [Fact]
    public void Rejects_AFormatItDoesNotKnow()
    {
        Assert.False(AssetDownloadFormats.TryParse("png", out _));
    }

    [Theory]
    [InlineData(".dng")]
    [InlineData(".DNG")]
    [InlineData(".cr3")]
    [InlineData(".heic")]
    [InlineData(".HEIF")]
    public void RawAndHeic_HaveAChoice(string extension)
    {
        Assert.True(AssetDownloadFormats.IsConvertible(extension));
    }

    [Theory]
    [InlineData(".jpg")]
    [InlineData(".png")]
    [InlineData(".mp4")]
    [InlineData("")]
    public void EverythingElse_HasNone(string extension)
    {
        Assert.False(AssetDownloadFormats.IsConvertible(extension));
        Assert.False(AssetDownloadFormats.ConvertsOnDownload(extension, AssetDownloadFormat.Jpeg));
        Assert.False(AssetDownloadFormats.ConvertsInZip(extension, AssetDownloadFormat.Jpeg));
    }

    [Theory]
    [InlineData(".dng", AssetDownloadFormat.Jpeg, true)]
    [InlineData(".heic", AssetDownloadFormat.Jpeg, true)]
    [InlineData(".dng", AssetDownloadFormat.Original, false)]
    // The original of a HEIC could not be downloaded at all before the choice.
    [InlineData(".heic", AssetDownloadFormat.Original, false)]
    public void SingleDownload_FollowsTheChoice(string extension, AssetDownloadFormat format, bool converts)
    {
        Assert.Equal(converts, AssetDownloadFormats.ConvertsOnDownload(extension, format));
    }

    [Fact]
    public void SingleDownload_WithoutAChoice_DoesWhatItAlwaysDid()
    {
        // Apps installed before the choice existed send no format.
        Assert.True(AssetDownloadFormats.ConvertsOnDownload(".heic", AssetDownloadFormat.Default));
        Assert.False(AssetDownloadFormats.ConvertsOnDownload(".dng", AssetDownloadFormat.Default));
    }

    [Fact]
    public void Zip_WithoutAChoice_CarriesOriginals()
    {
        Assert.False(AssetDownloadFormats.ConvertsInZip(".heic", AssetDownloadFormat.Default));
        Assert.False(AssetDownloadFormats.ConvertsInZip(".dng", AssetDownloadFormat.Default));
        Assert.False(AssetDownloadFormats.ConvertsInZip(".dng", AssetDownloadFormat.Original));
        Assert.True(AssetDownloadFormats.ConvertsInZip(".dng", AssetDownloadFormat.Jpeg));
        Assert.True(AssetDownloadFormats.ConvertsInZip(".heic", AssetDownloadFormat.Jpeg));
    }

    [Fact]
    public void JpegName_ReplacesTheExtension()
    {
        Assert.Equal("IMG_0042.jpg", AssetDownloadFormats.JpegFileName("IMG_0042.DNG"));
        Assert.Equal("viaje.2026.jpg", AssetDownloadFormats.JpegFileName("viaje.2026.heic"));
    }

    [Fact]
    public void Options_CountTheRawAndHeicOfTheSelection()
    {
        var options = DownloadOptionsEndpoint.Summarize(
            ["IMG_1.DNG", "IMG_2.dng", "IMG_3.HEIC", "IMG_4.jpg", "clip.mp4"]);

        Assert.Equal(5, options.Total);
        Assert.Equal(3, options.ConvertibleCount);
        Assert.Equal(["dng", "heic"], options.Extensions);
    }

    [Fact]
    public void Options_ForASelectionWithNothingToAsk()
    {
        var options = DownloadOptionsEndpoint.Summarize(["IMG_4.jpg", "clip.mp4"]);

        Assert.Equal(2, options.Total);
        Assert.Equal(0, options.ConvertibleCount);
        Assert.Empty(options.Extensions);
    }
}
