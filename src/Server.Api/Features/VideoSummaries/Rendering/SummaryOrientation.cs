using ImageMagick;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using SixLabors.ImageSharp;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>
/// Sorts material by shape for <see cref="VideoSummaryOrientation"/>. Near
/// square (within <see cref="SquareTolerance"/>) fits either frame; material
/// whose shape isn't known yet only goes into an "all" summary.
/// </summary>
public static class SummaryOrientation
{
    public const double SquareTolerance = 0.05;

    public static bool Matches(VideoSummaryOrientation filter, double? aspectRatio) => filter switch
    {
        VideoSummaryOrientation.Landscape => aspectRatio >= 1 - SquareTolerance,
        VideoSummaryOrientation.Portrait => aspectRatio <= 1 + SquareTolerance,
        _ => true,
    };

    /// <summary>The summary's own frame: portrait only when it's made of portrait material.</summary>
    public static (int Width, int Height) Frame(VideoSummaryOrientation filter) =>
        filter == VideoSummaryOrientation.Portrait ? (1080, 1920) : (1920, 1080);

    /// <summary>A photo's shape from its EXIF size and orientation tag
    /// (5–8 are the quarter turns, which swap width and height).</summary>
    public static double? PhotoAspectRatio(int? width, int? height, int? exifOrientation)
    {
        if (width is not > 0 || height is not > 0) return null;
        return exifOrientation is >= 5 and <= 8
            ? (double)height.Value / width.Value
            : (double)width.Value / height.Value;
    }

    /// <summary>A photo's shape read from the file's header, for photos the
    /// enrichment hasn't reached yet (no thumbnail, no EXIF row). Null when
    /// the file can't be read.</summary>
    public static async Task<double?> ReadPhotoAspectRatioAsync(string path, CancellationToken ct)
    {
        try
        {
            var extension = Path.GetExtension(path);
            if (MediaFileTypes.IsHeic(extension) || MediaFileTypes.IsRaw(extension))
            {
                var info = new MagickImageInfo(path);
                var turned = info.Orientation is OrientationType.LeftTop or OrientationType.RightTop
                    or OrientationType.RightBottom or OrientationType.LeftBottom;
                return PhotoAspectRatio((int)info.Width, (int)info.Height, turned ? 6 : 1);
            }

            var image = await Image.IdentifyAsync(path, ct);
            int? orientation = image.Metadata.ExifProfile?.TryGetValue(SixLabors.ImageSharp.Metadata.Profiles.Exif.ExifTag.Orientation, out var value) == true
                ? value.Value
                : null;
            return PhotoAspectRatio(image.Width, image.Height, orientation);
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception)
        {
            return null;
        }
    }
}
