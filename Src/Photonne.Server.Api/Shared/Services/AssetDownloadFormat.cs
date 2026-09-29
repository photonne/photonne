namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// What a download of a RAW or HEIC/HEIF hands over. <see cref="Default"/> is
/// a request that does not say: apps installed before the choice existed.
/// </summary>
public enum AssetDownloadFormat
{
    Default,
    Original,
    Jpeg,
}

/// <summary>
/// The rules behind "original or JPG?". Only RAW and HEIC/HEIF have the
/// choice: every other format is always downloaded as it is.
/// </summary>
public static class AssetDownloadFormats
{
    /// <summary>False when the value is not one the API knows.</summary>
    public static bool TryParse(string? value, out AssetDownloadFormat format)
    {
        format = AssetDownloadFormat.Default;
        if (string.IsNullOrWhiteSpace(value)) return true;

        if (value.Equals("original", StringComparison.OrdinalIgnoreCase))
        {
            format = AssetDownloadFormat.Original;
            return true;
        }
        if (value.Equals("jpeg", StringComparison.OrdinalIgnoreCase)
            || value.Equals("jpg", StringComparison.OrdinalIgnoreCase))
        {
            format = AssetDownloadFormat.Jpeg;
            return true;
        }
        return false;
    }

    public static bool IsHeic(string extension) =>
        extension.Equals(".heic", StringComparison.OrdinalIgnoreCase)
        || extension.Equals(".heif", StringComparison.OrdinalIgnoreCase);

    /// <summary>True for the formats that can leave as a JPEG: RAW and HEIC/HEIF.</summary>
    public static bool IsConvertible(string extension) =>
        IsHeic(extension) || RawImageLoader.IsRawExtension(extension);

    /// <summary>
    /// Whether a single download converts the file. Without a format the
    /// answer is what the endpoint always did, so older apps see no change:
    /// a HEIC left as JPEG and a RAW left untouched.
    /// </summary>
    public static bool ConvertsOnDownload(string extension, AssetDownloadFormat format) => format switch
    {
        AssetDownloadFormat.Jpeg => IsConvertible(extension),
        AssetDownloadFormat.Original => false,
        _ => IsHeic(extension),
    };

    /// <summary>
    /// Whether an entry of a ZIP is converted. A ZIP always carried originals,
    /// HEIC included, so that is what a request without a format still gets.
    /// </summary>
    public static bool ConvertsInZip(string extension, AssetDownloadFormat format) =>
        format == AssetDownloadFormat.Jpeg && IsConvertible(extension);

    public static string JpegFileName(string fileName) =>
        Path.GetFileNameWithoutExtension(fileName) + ".jpg";
}
