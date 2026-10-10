using ImageMagick;
using Microsoft.AspNetCore.Http.HttpResults;
using Photonne.Server.Api.Features.Share;
using Photonne.Server.Api.Tests.Fixtures;

namespace Photonne.Server.Api.Tests.Share;

/// <summary>
/// What a shared link hands to the browser for the formats it can't paint.
/// Exercises the conversion rule on its own, with files on disk: no link, no
/// database.
/// </summary>
public sealed class ShareMediaContentTests : IDisposable
{
    private readonly string _dir = Directory.CreateTempSubdirectory("photonne-share-").FullName;

    public void Dispose() => Directory.Delete(_dir, recursive: true);

    [Fact]
    public void Heic_IsServedAsJpeg_ToLookAt()
    {
        var result = ShareMediaEndpoint.TryServeAsJpeg(FixturePaths.Heic, "IMG_0001.HEIC", download: null);

        var file = Assert.IsType<FileContentHttpResult>(result);
        Assert.Equal("image/jpeg", file.ContentType);
        Assert.Null(file.FileDownloadName);
        AssertIsJpeg(file, width: 100, height: 100);
    }

    [Fact]
    public void Heic_IsDownloadedAsJpeg_WithAJpgName()
    {
        var result = ShareMediaEndpoint.TryServeAsJpeg(FixturePaths.Heic, "IMG_0001.HEIC", download: true);

        var file = Assert.IsType<FileContentHttpResult>(result);
        Assert.Equal("image/jpeg", file.ContentType);
        Assert.Equal("IMG_0001.jpg", file.FileDownloadName);
        AssertIsJpeg(file, width: 100, height: 100);
    }

    [Fact]
    public void Raw_IsServedAsJpeg_ToLookAt()
    {
        var result = ShareMediaEndpoint.TryServeAsJpeg(RenamedJpeg("photo.dng"), "photo.dng", download: false);

        var file = Assert.IsType<FileContentHttpResult>(result);
        Assert.Equal("image/jpeg", file.ContentType);
    }

    [Fact]
    public void Raw_IsDownloadedUntouched()
    {
        Assert.Null(ShareMediaEndpoint.TryServeAsJpeg(RenamedJpeg("photo.dng"), "photo.dng", download: true));
    }

    [Fact]
    public void Jpeg_IsServedAsItIs()
    {
        Assert.Null(ShareMediaEndpoint.TryServeAsJpeg(FixturePaths.WithExif, "photo.jpg", download: null));
    }

    [Fact]
    public void HeicThatCannotBeRead_FallsBackToTheOriginal()
    {
        var path = Path.Combine(_dir, "broken.heic");
        File.WriteAllText(path, "this is not an image");

        Assert.Null(ShareMediaEndpoint.TryServeAsJpeg(path, "broken.heic", download: null));
    }

    private string RenamedJpeg(string name)
    {
        var path = Path.Combine(_dir, name);
        File.Copy(FixturePaths.WithExif, path);
        return path;
    }

    private static void AssertIsJpeg(FileContentHttpResult file, uint width, uint height)
    {
        using var image = new MagickImage(file.FileContents.ToArray());
        Assert.Equal(MagickFormat.Jpeg, image.Format);
        Assert.Equal(width, image.Width);
        Assert.Equal(height, image.Height);
    }
}
