namespace Photonne.Server.Api.Shared.Services.Video;

/// <summary>
/// Where the playback copies go and how much of that disk they leave free:
/// <c>Video:PlaybackPath</c> (default <c>{ThumbnailsPath}/.playback</c>, which
/// is usually the fast, small disk) and <c>Video:MinFreeSpaceGb</c> (default
/// 10; 0 turns the check off). A copy is only started when the disk keeps
/// that much free after it, counting the copy as big as its original: an
/// H.264 copy of a 1080p HEVC can be bigger than the HEVC.
/// </summary>
public sealed record VideoPlaybackStorage(string Root, long MinFreeBytes)
{
    public const int DefaultMinFreeSpaceGb = 10;
    private const long BytesPerGb = 1024L * 1024 * 1024;

    public static VideoPlaybackStorage From(IConfiguration configuration)
    {
        var root = configuration["Video:PlaybackPath"] is { } path && !string.IsNullOrWhiteSpace(path)
            ? path.Trim()
            : Path.Combine(configuration["ThumbnailsPath"] ?? "/data/thumbnails", ".playback");
        var minFreeGb = Math.Max(0, configuration.GetValue("Video:MinFreeSpaceGb", DefaultMinFreeSpaceGb));
        return new VideoPlaybackStorage(root, minFreeGb * BytesPerGb);
    }

    /// <summary>Whether a copy of <paramref name="sourceBytes"/> still leaves the reserve free.</summary>
    public bool HasRoom(long availableBytes, long sourceBytes) =>
        MinFreeBytes <= 0 || availableBytes - sourceBytes >= MinFreeBytes;

    /// <summary>Free bytes on the disk that holds <see cref="Root"/>, or null when it can't be read.</summary>
    public long? AvailableBytes()
    {
        try
        {
            Directory.CreateDirectory(Root);
            return new DriveInfo(Root).AvailableFreeSpace;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or ArgumentException)
        {
            return null;
        }
    }
}
