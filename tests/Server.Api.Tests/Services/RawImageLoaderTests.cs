using System.Buffers.Binary;
using ImageMagick;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// RawImageLoader against the two kinds of DNG the RAW decoder rejects. The
/// files are built here, byte by byte: a real JPEG XL DNG weighs 40 MB, and
/// what matters to the loader is the TIFF structure around the image data.
/// No database involved.
/// </summary>
public sealed class RawImageLoaderTests : IDisposable
{
    private const int CompressionJpeg = 7;
    private const int CompressionJpegXl = 52546;
    private const int PhotometricYCbCr = 6;
    private const int PhotometricCfa = 32803;
    private const int PhotometricLinearRaw = 34892;

    private readonly string _dir = Directory.CreateTempSubdirectory("photonne-raw-").FullName;

    public void Dispose() => Directory.Delete(_dir, recursive: true);

    [Fact]
    public void JpegSavedAsDng_IsReadByContent()
    {
        var path = Path.Combine(_dir, "renamed.dng");
        File.WriteAllBytes(path, Jpeg(width: 120, height: 80));

        using var image = RawImageLoader.Load(path);

        Assert.Equal(MagickFormat.Jpeg, image.Format);
        Assert.Equal(120u, image.Width);
        Assert.Equal(80u, image.Height);
    }

    [Fact]
    public void JpegXlDng_FallsBackToTheEmbeddedPreview_Upright()
    {
        // The Samsung Expert RAW layout: main image in IFD0 (JPEG XL, shot
        // sideways), full-size JPEG preview in a SubIFD with no orientation.
        var path = WriteDng("expert-raw.dng", orientation: 6, mainWidth: 4000, mainHeight: 3000,
            previews: [(Jpeg(width: 400, height: 300), PhotometricYCbCr)]);

        using var image = new MagickImage(RawImageLoader.RenderJpeg(path));

        Assert.Equal(300u, image.Width);
        Assert.Equal(400u, image.Height);
    }

    [Fact]
    public void PreviewStoredUpright_IsNotRotatedAgain()
    {
        var path = WriteDng("pre-rotated.dng", orientation: 6, mainWidth: 4000, mainHeight: 3000,
            previews: [(Jpeg(width: 300, height: 400), PhotometricYCbCr)]);

        using var image = new MagickImage(RawImageLoader.RenderJpeg(path));

        Assert.Equal(300u, image.Width);
        Assert.Equal(400u, image.Height);
    }

    [Fact]
    public void PicksTheLargestPreview()
    {
        var small = Jpeg(width: 160, height: 120);
        var large = Jpeg(width: 800, height: 600);
        var path = WriteDng("two-previews.dng", orientation: 1, mainWidth: 4000, mainHeight: 3000,
            previews: [(small, PhotometricYCbCr), (large, PhotometricYCbCr)]);

        using var stream = File.OpenRead(path);
        var preview = RawImageLoader.FindEmbeddedPreview(stream);

        Assert.NotNull(preview);
        Assert.Equal(large.Length, preview!.Length);
    }

    [Fact]
    public void SensorDataInLosslessJpeg_IsNotMistakenForAPreview()
    {
        // Compression 7 is also how lossless-JPEG sensor data is stored. It
        // even starts with a JPEG marker; the photometric tag is what says it
        // is not a picture.
        var path = WriteDng("no-preview.dng", orientation: 1, mainWidth: 4000, mainHeight: 3000,
            previews: [(Jpeg(width: 400, height: 300), PhotometricCfa)]);

        using var stream = File.OpenRead(path);

        Assert.Null(RawImageLoader.FindEmbeddedPreview(stream));
    }

    [Fact]
    public void UnreadableWithoutPreview_ThrowsTheDecoderReason()
    {
        var path = WriteDng("nothing-to-show.dng", orientation: 1, mainWidth: 4000, mainHeight: 3000, previews: []);

        Assert.ThrowsAny<MagickException>(() => RawImageLoader.Load(path));
    }

    [Fact]
    public void NotATiff_HasNoPreview()
    {
        using var stream = new MemoryStream(Jpeg(width: 120, height: 80));

        Assert.Null(RawImageLoader.FindEmbeddedPreview(stream));
    }

    private static byte[] Jpeg(int width, int height)
    {
        // Noise, so the file clears the loader's minimum preview size.
        using var image = new MagickImage(MagickColors.SteelBlue, (uint)width, (uint)height);
        image.AddNoise(NoiseType.Gaussian);
        image.Format = MagickFormat.Jpeg;
        image.Quality = 90;
        return image.ToByteArray();
    }

    /// <summary>
    /// A little-endian DNG: IFD0 holds a main image no decoder can read
    /// (JPEG XL) and points to one SubIFD per preview.
    /// </summary>
    private string WriteDng(
        string name,
        int orientation,
        int mainWidth,
        int mainHeight,
        (byte[] Data, int Photometric)[] previews)
    {
        var mainData = new byte[2048];
        Random.Shared.NextBytes(mainData);

        const int headerSize = 8;
        const int ifd0Entries = 9;
        const int subIfdEntries = 7;
        var ifd0Size = IfdSize(ifd0Entries);
        var subIfdSize = IfdSize(subIfdEntries);

        var subIfdTableOffset = headerSize + ifd0Size;
        var subIfdsOffset = subIfdTableOffset + 4 * previews.Length;
        var dataOffset = subIfdsOffset + subIfdSize * previews.Length;

        using var stream = new MemoryStream();
        using var writer = new BinaryWriter(stream);

        writer.Write("II"u8);
        writer.Write((ushort)42);
        writer.Write((uint)headerSize);

        var mainOffset = dataOffset;
        var previewOffset = mainOffset + mainData.Length;

        writer.Write((ushort)ifd0Entries);
        WriteLong(writer, 254, 0);                       // NewSubFileType: main image
        WriteLong(writer, 256, mainWidth);
        WriteLong(writer, 257, mainHeight);
        WriteShort(writer, 259, CompressionJpegXl);
        WriteLong(writer, 262, PhotometricLinearRaw);
        WriteLong(writer, 273, mainOffset);              // StripOffsets
        WriteShort(writer, 274, orientation);
        WriteLong(writer, 279, mainData.Length);         // StripByteCounts
        // SubIFDs: inline when there is exactly one, a table otherwise.
        WriteEntry(writer, 330, type: 4, count: previews.Length,
            value: previews.Length == 1 ? subIfdsOffset : subIfdTableOffset);
        writer.Write(0u);                                // no next IFD

        for (var i = 0; i < previews.Length; i++)
            writer.Write((uint)(subIfdsOffset + subIfdSize * i));

        foreach (var (data, photometric) in previews)
        {
            using var preview = new MagickImage(data);
            writer.Write((ushort)subIfdEntries);
            WriteLong(writer, 254, 1);                   // NewSubFileType: preview
            WriteLong(writer, 256, (int)preview.Width);
            WriteLong(writer, 257, (int)preview.Height);
            WriteShort(writer, 259, CompressionJpeg);
            WriteLong(writer, 262, photometric);
            WriteLong(writer, 273, previewOffset);
            WriteLong(writer, 279, data.Length);
            writer.Write(0u);
            previewOffset += data.Length;
        }

        writer.Write(mainData);
        foreach (var (data, _) in previews) writer.Write(data);
        writer.Flush();

        var path = Path.Combine(_dir, name);
        File.WriteAllBytes(path, stream.ToArray());
        return path;
    }

    private static int IfdSize(int entries) => 2 + entries * 12 + 4;

    private static void WriteLong(BinaryWriter writer, ushort tag, int value) =>
        WriteEntry(writer, tag, type: 4, count: 1, value);

    private static void WriteShort(BinaryWriter writer, ushort tag, int value) =>
        WriteEntry(writer, tag, type: 3, count: 1, value);

    private static void WriteEntry(BinaryWriter writer, ushort tag, ushort type, int count, int value)
    {
        Span<byte> entry = stackalloc byte[12];
        BinaryPrimitives.WriteUInt16LittleEndian(entry, tag);
        BinaryPrimitives.WriteUInt16LittleEndian(entry[2..], type);
        BinaryPrimitives.WriteUInt32LittleEndian(entry[4..], (uint)count);
        if (type == 3)
            BinaryPrimitives.WriteUInt16LittleEndian(entry[8..], (ushort)value);
        else
            BinaryPrimitives.WriteUInt32LittleEndian(entry[8..], (uint)value);
        writer.Write(entry);
    }
}
