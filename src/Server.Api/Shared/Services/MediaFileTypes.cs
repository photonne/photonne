using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// The one place that says what a file is from its extension.
///
/// There used to be a list per service, and they drifted. When the RAW formats
/// were added, the upload endpoint's list was left behind: every DNG uploaded
/// from the app was stored as a video (video badge in the grid, the viewer
/// trying to play it, no ML pass). The indexer's video list was shorter than
/// the scanner's, so an .mpg read from disk was stored as a photo.
///
/// A new format is added here and nowhere else. Every method takes the
/// extension with or without the dot, in any case.
/// </summary>
public static class MediaFileTypes
{
    private static readonly HashSet<string> StandardImageExtensions = new(StringComparer.Ordinal)
    {
        ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".gif", ".webp",
    };

    private static readonly HashSet<string> HeicExtensions = new(StringComparer.Ordinal)
    {
        ".heic", ".heif",
    };

    private static readonly HashSet<string> RawExtensions = new(StringComparer.Ordinal)
    {
        ".raw", ".cr2", ".cr3", ".nef", ".arw", ".dng", ".orf", ".rw2", ".pef", ".raf", ".srw",
    };

    private static readonly HashSet<string> VideoFileExtensions = new(StringComparer.Ordinal)
    {
        ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm", ".m4v", ".3gp", ".mpeg", ".mpg",
        ".3g2", ".3gpp", ".amv", ".asf", ".f4v", ".m2v", ".mp2", ".mpe", ".mpv", ".ogv", ".qt", ".vob",
    };

    /// <summary>Every image extension: standard, HEIC/HEIF and RAW.</summary>
    public static IReadOnlyCollection<string> ImageExtensions { get; } =
        StandardImageExtensions.Concat(HeicExtensions).Concat(RawExtensions).ToArray();

    public static IReadOnlyCollection<string> VideoExtensions { get; } = VideoFileExtensions.ToArray();

    /// <summary>Lower case and with the dot; empty when there is no extension.</summary>
    public static string Normalize(string? extension)
    {
        if (string.IsNullOrWhiteSpace(extension)) return string.Empty;
        var normalized = extension.Trim().ToLowerInvariant();
        return normalized.StartsWith('.') ? normalized : "." + normalized;
    }

    public static bool IsHeic(string? extension) => HeicExtensions.Contains(Normalize(extension));

    public static bool IsRaw(string? extension) => RawExtensions.Contains(Normalize(extension));

    public static bool IsImage(string? extension)
    {
        var normalized = Normalize(extension);
        return StandardImageExtensions.Contains(normalized)
            || HeicExtensions.Contains(normalized)
            || RawExtensions.Contains(normalized);
    }

    public static bool IsVideo(string? extension) => VideoFileExtensions.Contains(Normalize(extension));

    /// <summary>True for what the library indexes: an image or a video.</summary>
    public static bool IsSupported(string? extension) => IsImage(extension) || IsVideo(extension);

    /// <summary>
    /// The type an asset is stored with. Video only for a known video
    /// extension: an extension nobody knows is not something to try to play.
    /// </summary>
    public static AssetType Classify(string? extension) =>
        IsVideo(extension) ? AssetType.Video : AssetType.Image;
}
