using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Media;

/// <summary>
/// Byte-level checks for locating the MP4 embedded inside a Samsung/Google
/// motion photo. Uses synthesized files (minimal JPEG SOI..EOI + a minimal
/// ftyp box) rather than real fixtures so the math is pinned without shipping
/// binary samples.
/// </summary>
public sealed class EmbeddedMotionPhotoExtractorTests
{
    // Minimal JPEG: SOI marker, filler, EOI marker.
    private static byte[] FakeJpeg(int filler = 100)
    {
        var body = new List<byte> { 0xFF, 0xD8 };
        body.AddRange(new byte[filler]);
        body.Add(0xFF);
        body.Add(0xD9);
        return body.ToArray();
    }

    // Minimal ISO-BMFF ftyp box: size(4) + 'ftyp' + major brand + minor + 1
    // compatible brand = 24 bytes.
    private static byte[] FakeFtypBox()
    {
        return new byte[]
        {
            0x00, 0x00, 0x00, 0x18,                         // size = 24
            (byte)'f', (byte)'t', (byte)'y', (byte)'p',     // 'ftyp'
            (byte)'m', (byte)'p', (byte)'4', (byte)'2',     // major brand
            0x00, 0x00, 0x00, 0x00,                         // minor version
            (byte)'i', (byte)'s', (byte)'o', (byte)'m',     // compatible brand
            (byte)'m', (byte)'p', (byte)'4', (byte)'2',     // compatible brand
        };
    }

    private static string WriteTemp(byte[] bytes, string ext = ".jpg")
    {
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ext);
        File.WriteAllBytes(path, bytes);
        return path;
    }

    [Fact]
    public void ResolveEmbeddedVideo_FindsAppendedMp4()
    {
        var jpeg = FakeJpeg();
        var mp4 = FakeFtypBox();
        var path = WriteTemp(jpeg.Concat(mp4).ToArray());
        try
        {
            var range = EmbeddedMotionPhotoExtractor.ResolveEmbeddedVideo(path);

            Assert.NotNull(range);
            Assert.Equal(jpeg.Length, range!.Value.Offset);
            Assert.Equal(mp4.Length, range.Value.Length);

            // The slice at Offset must begin with the ftyp box.
            using var fs = File.OpenRead(path);
            fs.Position = range.Value.Offset;
            var head = new byte[8];
            fs.ReadExactly(head);
            Assert.Equal((byte)'f', head[4]);
            Assert.Equal((byte)'t', head[5]);
            Assert.Equal((byte)'y', head[6]);
            Assert.Equal((byte)'p', head[7]);
        }
        finally { File.Delete(path); }
    }

    [Fact]
    public void ResolveEmbeddedVideo_PlainJpeg_ReturnsNull()
    {
        var path = WriteTemp(FakeJpeg());
        try
        {
            Assert.Null(EmbeddedMotionPhotoExtractor.ResolveEmbeddedVideo(path));
        }
        finally { File.Delete(path); }
    }

    [Fact]
    public void IsEmbeddedMotionPhoto_LegacySamsungTrailer_True()
    {
        // No Google XMP, but the file ends with the Samsung footer marker.
        var bytes = FakeJpeg()
            .Concat(FakeFtypBox())
            .Concat(System.Text.Encoding.ASCII.GetBytes("MotionPhoto_Data"))
            .ToArray();
        var path = WriteTemp(bytes);
        try
        {
            Assert.True(EmbeddedMotionPhotoExtractor.IsEmbeddedMotionPhoto(path));
        }
        finally { File.Delete(path); }
    }

    [Fact]
    public void IsEmbeddedMotionPhoto_PlainJpeg_False()
    {
        var path = WriteTemp(FakeJpeg());
        try
        {
            Assert.False(EmbeddedMotionPhotoExtractor.IsEmbeddedMotionPhoto(path));
        }
        finally { File.Delete(path); }
    }

    [Fact]
    public void IsCandidateExtension_OnlyStillImages()
    {
        Assert.True(EmbeddedMotionPhotoExtractor.IsCandidateExtension("a.jpg"));
        Assert.True(EmbeddedMotionPhotoExtractor.IsCandidateExtension("a.JPEG"));
        Assert.True(EmbeddedMotionPhotoExtractor.IsCandidateExtension("a.heic"));
        Assert.False(EmbeddedMotionPhotoExtractor.IsCandidateExtension("a.mp4"));
        Assert.False(EmbeddedMotionPhotoExtractor.IsCandidateExtension("a.png"));
    }
}
