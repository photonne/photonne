namespace Photonne.Server.Api.Tests.Fixtures;

/// <summary>
/// Resolves absolute paths to the test fixture files that are copied next to
/// the test assembly (see the &lt;Content Include="Fixtures\*"&gt; block in the
/// test csproj).
/// </summary>
public static class FixturePaths
{
    public static string Root => Path.Combine(AppContext.BaseDirectory, "Fixtures");

    /// <summary>
    /// 100×100 JPEG with a full set of EXIF tags: Canon EOS R5, ISO 800,
    /// Orientation=1, DateTimeOriginal=2024-01-15 12:30:45, GPS Madrid (N/W).
    /// </summary>
    public static string WithExif => Path.Combine(Root, "sample-with-exif.jpg");

    /// <summary>100×100 JPEG without any EXIF tags.</summary>
    public static string NoMetadata => Path.Combine(Root, "sample-no-metadata.jpg");

    /// <summary>
    /// 100×100 JPEG with GPS set to Buenos Aires (S/W) so the sign handling
    /// via GPSLatitudeRef / GPSLongitudeRef can be verified.
    /// </summary>
    public static string NegativeGps => Path.Combine(Root, "sample-negative-gps.jpg");

    /// <summary>
    /// Minimal H.264 MP4: 64×64, 1 second, teal, generated with
    /// <c>ffmpeg -f lavfi -i color=c=teal:s=64x64:d=1 -c:v libx264</c>.
    /// </summary>
    public static string Video => Path.Combine(Root, "sample-video.mp4");

    /// <summary>
    /// Same clip muxed as QuickTime MOV with an Apple-style ISO 6709
    /// location (Madrid, N/W), generated with
    /// <c>ffmpeg -i sample-video.mp4 -metadata location=+40.4168-003.7038/
    /// -c copy sample-video-gps.mov</c>. Exercises the QuickTime GPS branch
    /// of the extractor (videos carry no EXIF GpsDirectory).
    /// </summary>
    public static string VideoWithGps => Path.Combine(Root, "sample-video-gps.mov");

    /// <summary>
    /// Same clip as plain ISO MP4 with the GPS written Android-style: an
    /// ISO 6709 string (Buenos Aires, S/W) in the legacy moov/udta/©xyz
    /// atom, which MetadataExtractor does not surface — exercises the
    /// manual udta scan fallback.
    /// </summary>
    public static string VideoWithXyzGps => Path.Combine(Root, "sample-video-gps-xyz.mp4");
}
