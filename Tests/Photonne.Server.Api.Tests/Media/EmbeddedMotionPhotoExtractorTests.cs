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
    public void IsEmbeddedMotionPhoto_BareSamsungFooter_False()
    {
        // A Samsung photo with an SEF footer but no motion block (e.g. a portrait
        // or dual-camera capture). The bare "SEFH" marker must NOT be treated as a
        // motion photo — only the "MotionPhoto_Data" block name counts.
        var bytes = FakeJpeg()
            .Concat(System.Text.Encoding.ASCII.GetBytes("DualShot_Meta_Info"))
            .Concat(System.Text.Encoding.ASCII.GetBytes("SEFH"))
            .ToArray();
        var path = WriteTemp(bytes);
        try
        {
            Assert.False(EmbeddedMotionPhotoExtractor.IsEmbeddedMotionPhoto(path));
        }
        finally { File.Delete(path); }
    }

    [Theory]
    // Explicit flags set to "1" are reliable motion-photo markers.
    [InlineData("1", null, null, true)]
    [InlineData(null, "1", null, true)]
    // A positive MicroVideoOffset points at real appended bytes.
    [InlineData(null, null, "123", true)]
    // Ordinary captures write the flags/offset as "0" — these are NOT motion photos.
    [InlineData("0", "0", "0", false)]
    [InlineData(null, null, "0", false)]
    [InlineData(null, null, null, false)]
    public void IsMotionXmpPositive_DistinguishesGenuineMotionPhotos(
        string? motionPhoto, string? microVideo, string? microOffset, bool expected)
    {
        Assert.Equal(expected,
            EmbeddedMotionPhotoExtractor.IsMotionXmpPositive(motionPhoto, microVideo, microOffset));
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
