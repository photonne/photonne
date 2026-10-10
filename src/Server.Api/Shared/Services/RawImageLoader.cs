using System.Buffers.Binary;
using ImageMagick;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Opens RAW (and HEIC) files with Magick.NET, with a way out for the files
/// its RAW decoder (LibRaw) cannot read. Measured against 258 real DNGs, two
/// kinds fail, both with "Unsupported file format or not RAW file":
///  - DNG 1.7 compressed with JPEG XL (Samsung Expert RAW, recent ProRAW):
///    LibRaw has no decoder for compression 52546.
///  - Files that are not what their extension says (a JPEG saved as .dng):
///    given a path, Magick trusts the extension and forces the RAW reader.
///
/// The decoder always goes first, so files that already worked keep the exact
/// same rendering. Only when it throws:
///  - a file that is not TIFF-based is read again by content, not by name;
///  - a TIFF-based RAW falls back to its largest embedded JPEG preview, the
///    picture the camera rendered itself.
///
/// Developing the JPEG XL data ourselves was tried and dropped: it decodes,
/// but it is linear sensor data (no white balance, no colour matrix) and comes
/// out dark and green.
///
/// Stateless and static, mirroring the other file-reading helpers
/// (<see cref="EmbeddedMotionPhotoExtractor"/>).
/// </summary>
public static class RawImageLoader
{
    private const ushort TagNewSubFileType = 254;
    private const ushort TagImageWidth = 256;
    private const ushort TagImageLength = 257;
    private const ushort TagCompression = 259;
    private const ushort TagPhotometric = 262;
    private const ushort TagStripOffsets = 273;
    private const ushort TagOrientation = 274;
    private const ushort TagStripByteCounts = 279;
    private const ushort TagTileOffsets = 324;
    private const ushort TagTileByteCounts = 325;
    private const ushort TagSubIfds = 330;
    private const ushort TagJpegInterchangeFormat = 513;
    private const ushort TagJpegInterchangeFormatLength = 514;

    private const int CompressionJpeg = 7;
    private const int PhotometricRgb = 2;
    private const int PhotometricYCbCr = 6;

    // A malformed file must not send the parser around in circles or make it
    // allocate the whole RAW as a "preview".
    private const int MaxIfds = 32;
    private const int MaxIfdDepth = 3;
    private const int MaxEntriesPerIfd = 512;
    private const long MinPreviewBytes = 1024;
    private const long MaxPreviewBytes = 64L * 1024 * 1024;

    /// <summary>
    /// Opens the file. Throws the decoder's own exception when neither the
    /// decoder nor the fallbacks could read it, so the caller records the real
    /// reason.
    /// </summary>
    public static MagickImage Load(string filePath)
    {
        try
        {
            return new MagickImage(filePath);
        }
        catch (MagickException ex)
        {
            var fallback = TryLoadByContent(filePath) ?? TryLoadEmbeddedPreview(filePath);
            if (fallback == null) throw;

            Console.WriteLine(
                $"[RAW] {Path.GetFileName(filePath)}: decoder failed ({ex.Message}); " +
                $"using {fallback.Format} {fallback.Width}x{fallback.Height} instead");
            return fallback;
        }
    }

    /// <summary>
    /// The file as a JPEG a browser or an image view can paint, upright.
    /// </summary>
    public static byte[] RenderJpeg(string filePath, int quality = 90)
    {
        using var image = Load(filePath);
        image.AutoOrient();
        image.Format = MagickFormat.Jpeg;
        image.Quality = (uint)quality;
        return image.ToByteArray();
    }

    /// <summary>
    /// Reads a file that is not TIFF-based from a stream, where Magick has no
    /// extension to trust and detects the format from the bytes.
    /// </summary>
    private static MagickImage? TryLoadByContent(string filePath)
    {
        try
        {
            using var stream = File.OpenRead(filePath);
            Span<byte> header = stackalloc byte[4];
            if (stream.Read(header) < 4 || IsTiffHeader(header)) return null;

            stream.Seek(0, SeekOrigin.Begin);
            return new MagickImage(stream);
        }
        catch (Exception ex) when (ex is MagickException or IOException)
        {
            return null;
        }
    }

    private static MagickImage? TryLoadEmbeddedPreview(string filePath)
    {
        try
        {
            using var stream = File.OpenRead(filePath);
            var preview = FindEmbeddedPreview(stream);
            if (preview == null) return null;

            var bytes = new byte[preview.Length];
            stream.Seek(preview.Offset, SeekOrigin.Begin);
            stream.ReadExactly(bytes);

            var image = new MagickImage(bytes);
            // The preview rarely says which way is up: that lives in the RAW's
            // main IFD. Callers already run AutoOrient(), so hand it over.
            if (preview.Orientation is >= 1 and <= 8
                && image.Orientation is OrientationType.Undefined or OrientationType.TopLeft
                && !IsAlreadyRotated(preview, image))
            {
                image.Orientation = (OrientationType)preview.Orientation;
            }
            return image;
        }
        catch (Exception ex) when (ex is MagickException or IOException)
        {
            return null;
        }
    }

    /// <summary>
    /// A preview stored upright next to a sideways sensor image has the
    /// opposite shape (portrait vs landscape); rotating it again would tip it.
    /// </summary>
    private static bool IsAlreadyRotated(EmbeddedPreview preview, MagickImage image)
    {
        if (preview.MainWidth == 0 || preview.MainHeight == 0) return false;
        if (preview.MainWidth == preview.MainHeight || image.Width == image.Height) return false;
        return (preview.MainWidth > preview.MainHeight) != (image.Width > image.Height);
    }

    internal sealed record EmbeddedPreview(long Offset, long Length, int Orientation, long MainWidth, long MainHeight);

    /// <summary>
    /// Walks the TIFF structure (IFD chain plus SubIFDs) and returns the
    /// largest embedded JPEG, or null when the file is not a TIFF or has none.
    /// </summary>
    internal static EmbeddedPreview? FindEmbeddedPreview(Stream stream)
    {
        if (stream.Length < 8) return null;

        Span<byte> header = stackalloc byte[8];
        stream.Seek(0, SeekOrigin.Begin);
        stream.ReadExactly(header);
        if (!IsTiffHeader(header)) return null;

        var reader = new TiffReader(stream, littleEndian: header[0] == (byte)'I');
        var ifds = new List<Ifd>();
        var visited = new HashSet<long>();
        ReadIfdChain(reader, reader.ToUInt32(header[4..]), depth: 0, ifds, visited);
        if (ifds.Count == 0) return null;

        long bestOffset = 0, bestLength = 0;
        foreach (var ifd in ifds)
        {
            if (!TryGetJpeg(ifd, out var offset, out var length)) continue;
            if (length <= bestLength || !reader.StartsWithJpegMarker(offset, length)) continue;
            bestOffset = offset;
            bestLength = length;
        }
        if (bestLength == 0) return null;

        var main = ifds
            .Where(i => i.NewSubFileType == 0 && i.Width > 0 && i.Height > 0)
            .OrderByDescending(i => i.Width * i.Height)
            .FirstOrDefault();

        return new EmbeddedPreview(
            bestOffset,
            bestLength,
            // DNG keeps the orientation in the first IFD; it applies to the
            // main image and to every preview.
            Orientation: ifds[0].Orientation,
            MainWidth: main?.Width ?? 0,
            MainHeight: main?.Height ?? 0);
    }

    private static bool TryGetJpeg(Ifd ifd, out long offset, out long length)
    {
        offset = 0;
        length = 0;

        if (ifd.JpegOffset > 0 && ifd.JpegLength > 0)
        {
            // Old-style JPEG pointer: CR2, NEF, ARW and PEF previews.
            offset = ifd.JpegOffset;
            length = ifd.JpegLength;
        }
        else if (ifd.Compression == CompressionJpeg
                 // Lossless-JPEG sensor data is compression 7 too; what tells a
                 // picture from it is the photometric interpretation.
                 && ifd.Photometric is PhotometricRgb or PhotometricYCbCr
                 && ifd.DataOffsets.Count == 1 && ifd.DataByteCounts.Count == 1)
        {
            offset = ifd.DataOffsets[0];
            length = ifd.DataByteCounts[0];
        }

        return length is >= MinPreviewBytes and <= MaxPreviewBytes;
    }

    private static void ReadIfdChain(TiffReader reader, long offset, int depth, List<Ifd> ifds, HashSet<long> visited)
    {
        while (offset > 0 && ifds.Count < MaxIfds && visited.Add(offset))
        {
            var ifd = reader.ReadIfd(offset, out var next);
            if (ifd == null) return;
            ifds.Add(ifd);

            if (depth < MaxIfdDepth)
            {
                foreach (var sub in ifd.SubIfds)
                    ReadIfdChain(reader, sub, depth + 1, ifds, visited);
            }
            offset = next;
        }
    }

    private static bool IsTiffHeader(ReadOnlySpan<byte> header) =>
        header.Length >= 4
        && ((header[0] == (byte)'I' && header[1] == (byte)'I' && header[2] == 42 && header[3] == 0)
            || (header[0] == (byte)'M' && header[1] == (byte)'M' && header[2] == 0 && header[3] == 42));

    private sealed class Ifd
    {
        public long NewSubFileType;
        public long Width;
        public long Height;
        public long Compression;
        public long Photometric;
        public int Orientation;
        public long JpegOffset;
        public long JpegLength;
        public List<long> DataOffsets = new();
        public List<long> DataByteCounts = new();
        public List<long> SubIfds = new();
    }

    private sealed class TiffReader(Stream stream, bool littleEndian)
    {
        private const int EntrySize = 12;

        public uint ToUInt32(ReadOnlySpan<byte> bytes) => littleEndian
            ? BinaryPrimitives.ReadUInt32LittleEndian(bytes)
            : BinaryPrimitives.ReadUInt32BigEndian(bytes);

        private ushort ToUInt16(ReadOnlySpan<byte> bytes) => littleEndian
            ? BinaryPrimitives.ReadUInt16LittleEndian(bytes)
            : BinaryPrimitives.ReadUInt16BigEndian(bytes);

        public bool StartsWithJpegMarker(long offset, long length)
        {
            if (offset <= 0 || offset + length > stream.Length) return false;
            Span<byte> marker = stackalloc byte[2];
            stream.Seek(offset, SeekOrigin.Begin);
            return stream.Read(marker) == 2 && marker[0] == 0xFF && marker[1] == 0xD8;
        }

        public Ifd? ReadIfd(long offset, out long next)
        {
            next = 0;
            if (offset + 2 > stream.Length) return null;

            Span<byte> countBytes = stackalloc byte[2];
            stream.Seek(offset, SeekOrigin.Begin);
            stream.ReadExactly(countBytes);
            int count = ToUInt16(countBytes);
            if (count == 0 || count > MaxEntriesPerIfd) return null;

            var tableSize = count * EntrySize + 4;
            if (offset + 2 + tableSize > stream.Length) return null;
            var table = new byte[tableSize];
            stream.ReadExactly(table);
            next = ToUInt32(table.AsSpan(count * EntrySize));

            var ifd = new Ifd();
            for (var i = 0; i < count; i++)
            {
                var entry = table.AsSpan(i * EntrySize, EntrySize);
                var tag = ToUInt16(entry);
                switch (tag)
                {
                    case TagNewSubFileType: ifd.NewSubFileType = First(entry); break;
                    case TagImageWidth: ifd.Width = First(entry); break;
                    case TagImageLength: ifd.Height = First(entry); break;
                    case TagCompression: ifd.Compression = First(entry); break;
                    case TagPhotometric: ifd.Photometric = First(entry); break;
                    case TagOrientation: ifd.Orientation = (int)First(entry); break;
                    case TagJpegInterchangeFormat: ifd.JpegOffset = First(entry); break;
                    case TagJpegInterchangeFormatLength: ifd.JpegLength = First(entry); break;
                    case TagStripOffsets or TagTileOffsets: ifd.DataOffsets = ReadValues(entry, 2); break;
                    case TagStripByteCounts or TagTileByteCounts: ifd.DataByteCounts = ReadValues(entry, 2); break;
                    case TagSubIfds: ifd.SubIfds = ReadValues(entry, MaxIfds); break;
                }
            }
            return ifd;
        }

        private long First(ReadOnlySpan<byte> entry)
        {
            var values = ReadValues(entry, 1);
            return values.Count > 0 ? values[0] : 0;
        }

        /// <summary>
        /// Up to <paramref name="max"/> values of a SHORT or LONG entry. A
        /// longer entry returns max values, enough to tell "one" from "many".
        /// </summary>
        private List<long> ReadValues(ReadOnlySpan<byte> entry, int max)
        {
            var values = new List<long>();
            var type = ToUInt16(entry[2..]);
            var count = ToUInt32(entry[4..]);
            // 3 = SHORT, 4 = LONG, 13 = IFD. Anything else is not an offset,
            // a size or a small number.
            var size = type switch { 3 => 2, 4 or 13 => 4, _ => 0 };
            if (size == 0 || count == 0) return values;

            var take = (int)Math.Min(count, (uint)max);
            var buffer = new byte[take * size];
            if ((long)count * size <= 4)
            {
                entry.Slice(8, buffer.Length).CopyTo(buffer);
            }
            else
            {
                long offset = ToUInt32(entry[8..]);
                if (offset + buffer.Length > stream.Length) return values;
                var position = stream.Position;
                stream.Seek(offset, SeekOrigin.Begin);
                stream.ReadExactly(buffer);
                stream.Seek(position, SeekOrigin.Begin);
            }

            for (var i = 0; i < take; i++)
            {
                var value = buffer.AsSpan(i * size, size);
                values.Add(size == 2 ? ToUInt16(value) : ToUInt32(value));
            }
            return values;
        }
    }
}
