namespace Photonne.Server.Api.Shared.Services.Video;

/// <summary>What ffprobe says about a video, as far as playing it goes.</summary>
public sealed record VideoProbe(string Extension, string? VideoCodec, int Width, int Height, string? AudioCodec);

/// <summary>
/// Which videos need a playback copy. Browsers and phones all play H.264 with
/// AAC (or MP3) in MP4/MOV; HEVC from an iPhone doesn't play in Chrome or
/// Firefox on most desktops, ProRes, VP9-in-MOV, AVI or MKV play almost
/// nowhere, and a 4K original is a lot to stream to a phone on mobile data.
/// </summary>
public static class VideoPlaybackPolicy
{
    /// <summary>Longest side of the playback copy.</summary>
    public const int MaxLongSide = 1920;

    private static readonly HashSet<string> PlayableContainers = new(StringComparer.OrdinalIgnoreCase) { ".mp4", ".m4v", ".mov" };
    private static readonly HashSet<string> PlayableAudio = new(StringComparer.OrdinalIgnoreCase) { "aac", "mp3" };

    public static bool NeedsPlaybackCopy(VideoProbe probe) =>
        !PlayableContainers.Contains(probe.Extension)
        || !string.Equals(probe.VideoCodec, "h264", StringComparison.OrdinalIgnoreCase)
        || Math.Max(probe.Width, probe.Height) > MaxLongSide
        || (probe.AudioCodec is not null && !PlayableAudio.Contains(probe.AudioCodec));
}
