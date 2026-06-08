using System.Text;
using MetadataExtractor;
using MetadataExtractor.Formats.Xmp;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Detects and locates the MP4 video embedded inside a Samsung/Google
/// "motion photo" — a single JPEG (occasionally HEIC) with the clip appended
/// after the image data. This is distinct from an iOS Live Photo, which is two
/// sibling files (HEIC + MOV) handled by <see cref="MediaRecognitionService"/>
/// and the sibling lookup in the motion endpoint.
///
/// Two phases share one detection idea:
///  - <see cref="IsEmbeddedMotionPhoto"/> is the cheap recognition-time check
///    (XMP only, plus a bounded read of the file tail for legacy Samsung), used
///    to tag the asset <c>LivePhoto</c> so the client lights up its existing
///    "Ver foto en movimiento" affordance.
///  - <see cref="ResolveEmbeddedVideo"/> runs on demand when the motion endpoint
///    needs the actual clip: it returns the byte range of the appended MP4.
///
/// Stateless and static, mirroring the other file-reading helpers
/// (<see cref="ExifExtractorService"/>); nothing here touches the database.
/// </summary>
public static class EmbeddedMotionPhotoExtractor
{
    // Google's camera XMP namespace carries both the legacy MicroVideo tags and
    // the newer MotionPhoto flag; modern Samsung (One UI) writes the same.
    private const string GCameraNs = "http://ns.google.com/photos/1.0/camera/";

    private static readonly string[] CandidateExtensions = { ".jpg", ".jpeg", ".heic", ".heif" };

    // ISO-BMFF major brands that legitimately start an appended motion clip.
    // Used to validate an 'ftyp' match so we don't mistake JPEG entropy bytes
    // for the embedded video.
    private static readonly HashSet<string> Mp4Brands = new(StringComparer.Ordinal)
    {
        "mp42", "mp41", "mp4v", "isom", "iso2", "iso4", "iso5", "iso6",
        "avc1", "M4V ", "3gp4", "3gp5", "3gp6", "3gg6", "mmp4", "qt  ",
    };

    /// <summary>True when the extension is one a motion photo can use (a single
    /// still file, not a sibling clip). Cheap gate before any file read.</summary>
    public static bool IsCandidateExtension(string filePath) =>
        CandidateExtensions.Contains(Path.GetExtension(filePath).ToLowerInvariant());

    /// <summary>
    /// True when <paramref name="filePath"/> is a single-file motion photo with
    /// an embedded clip. Reads only metadata (XMP) plus a small tail of the file,
    /// never the whole image, so it is safe to call per-asset during enrichment.
    /// </summary>
    public static bool IsEmbeddedMotionPhoto(string filePath)
    {
        try
        {
            if (!IsCandidateExtension(filePath)) return false;
            return HasMotionXmp(filePath) || HasSamsungTrailer(filePath);
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// Byte range of the appended MP4 within the still, or null when none is
    /// found. <c>Offset</c> is where the MP4 starts; <c>Length</c> runs to the
    /// end of the file (the clip is the trailing data). Prefers the Google
    /// MicroVideoOffset hint and falls back to scanning for the MP4 <c>ftyp</c>
    /// box, validated by a known major brand.
    /// </summary>
    public static (long Offset, long Length)? ResolveEmbeddedVideo(string filePath)
    {
        try
        {
            var fileLength = new FileInfo(filePath).Length;

            // Fast path: Google MicroVideoOffset is the number of bytes from the
            // end of the file back to the start of the MP4.
            var micro = TryGetMicroVideoOffset(filePath);
            if (micro is { } off && off > 0 && off < fileLength)
            {
                var start = fileLength - off;
                if (LooksLikeFtypAt(filePath, start))
                    return (start, fileLength - start);
            }

            // General path: locate the appended MP4 by its 'ftyp' box.
            var scanned = ScanForMp4Start(filePath);
            if (scanned is { } s)
                return (s, fileLength - s);

            return null;
        }
        catch
        {
            return null;
        }
    }

    // ── XMP ──────────────────────────────────────────────────────────────────

    private static bool HasMotionXmp(string filePath)
    {
        foreach (var meta in ReadXmp(filePath))
        {
            try
            {
                var motionPhoto = meta.GetPropertyString(GCameraNs, "MotionPhoto");
                var microVideo = meta.GetPropertyString(GCameraNs, "MicroVideo");
                var microOffset = meta.GetPropertyString(GCameraNs, "MicroVideoOffset");
                if (motionPhoto == "1" || microVideo == "1" || !string.IsNullOrEmpty(microOffset))
                    return true;
            }
            catch { /* property absent or malformed path */ }
        }
        return false;
    }

    private static long? TryGetMicroVideoOffset(string filePath)
    {
        foreach (var meta in ReadXmp(filePath))
        {
            try
            {
                var raw = meta.GetPropertyString(GCameraNs, "MicroVideoOffset");
                if (long.TryParse(raw, out var off)) return off;
            }
            catch { /* property absent */ }
        }
        return null;
    }

    private static IEnumerable<XmpCore.IXmpMeta> ReadXmp(string filePath)
    {
        IReadOnlyList<MetadataExtractor.Directory> dirs;
        try
        {
            dirs = ImageMetadataReader.ReadMetadata(filePath);
        }
        catch
        {
            yield break;
        }
        foreach (var dir in dirs)
        {
            if (dir is XmpDirectory xmp && xmp.XmpMeta != null)
                yield return xmp.XmpMeta;
        }
    }

    // ── Legacy Samsung trailer ───────────────────────────────────────────────

    // Older Samsung motion photos carry no Google XMP; instead the file ends
    // with a Samsung Extended Format footer ("SEFH") that indexes appended
    // blocks, one of which is the motion video. A bounded tail read is enough to
    // recognize it; the exact clip bounds are still located via the 'ftyp' scan.
    private static bool HasSamsungTrailer(string filePath)
    {
        try
        {
            using var stream = File.OpenRead(filePath);
            var tail = (int)Math.Min(stream.Length, 16 * 1024);
            if (tail <= 0) return false;
            stream.Position = stream.Length - tail;
            var buffer = new byte[tail];
            stream.ReadExactly(buffer);
            var text = Encoding.Latin1.GetString(buffer);
            return text.Contains("MotionPhoto_Data", StringComparison.Ordinal)
                || text.Contains("SEFH", StringComparison.Ordinal);
        }
        catch
        {
            return false;
        }
    }

    // ── MP4 location ─────────────────────────────────────────────────────────

    private static bool LooksLikeFtypAt(string filePath, long boxStart)
    {
        try
        {
            using var stream = File.OpenRead(filePath);
            if (boxStart < 0 || boxStart + 12 > stream.Length) return false;
            stream.Position = boxStart;
            Span<byte> head = stackalloc byte[12];
            if (stream.Read(head) < 12) return false;
            // head[0..4] = box size, head[4..8] = 'ftyp', head[8..12] = major brand.
            if (!(head[4] == (byte)'f' && head[5] == (byte)'t' && head[6] == (byte)'y' && head[7] == (byte)'p'))
                return false;
            var brand = Encoding.Latin1.GetString(head[8..12]);
            return Mp4Brands.Contains(brand);
        }
        catch
        {
            return false;
        }
    }

    private static long? ScanForMp4Start(string filePath)
    {
        // Clips are a few MB; this only runs on demand for assets already known
        // to be motion photos, so a single full read is acceptable.
        byte[] bytes;
        try { bytes = File.ReadAllBytes(filePath); }
        catch { return null; }

        for (var i = 4; i + 12 <= bytes.Length; i++)
        {
            if (bytes[i] != (byte)'f' || bytes[i + 1] != (byte)'t' ||
                bytes[i + 2] != (byte)'y' || bytes[i + 3] != (byte)'p')
                continue;

            var boxStart = i - 4;
            long size = ((uint)bytes[boxStart] << 24) | ((uint)bytes[boxStart + 1] << 16)
                      | ((uint)bytes[boxStart + 2] << 8) | bytes[boxStart + 3];
            // An ftyp box is small; bound the size to reject coincidental matches
            // inside JPEG entropy data.
            if (size < 16 || size > 256 || boxStart + size > bytes.Length)
                continue;

            var brand = Encoding.Latin1.GetString(bytes, i + 4, 4);
            if (Mp4Brands.Contains(brand))
                return boxStart;
        }
        return null;
    }
}
