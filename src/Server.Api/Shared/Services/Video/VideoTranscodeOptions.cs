namespace Photonne.Server.Api.Shared.Services.Video;

/// <summary>Who encodes the playback copies.</summary>
public enum VideoEncoder
{
    /// <summary>libx264 on the CPU. Works everywhere.</summary>
    Software,

    /// <summary>
    /// h264_nvenc on an NVIDIA card, decoding on it too when it can. Needs the
    /// GPU passed through and <c>video</c> in NVIDIA_DRIVER_CAPABILITIES.
    /// </summary>
    Nvenc
}

/// <summary>
/// How the playback copies are encoded: <c>Video:Encoder</c>
/// (<c>software</c> or <c>nvenc</c>) and <c>Video:Threads</c> (0 lets
/// ffmpeg use every core it sees, which is every core of the host, not the
/// container's <c>cpus</c> limit).
/// </summary>
public sealed record VideoTranscodeOptions(VideoEncoder Encoder, int Threads)
{
    public static VideoTranscodeOptions From(IConfiguration configuration, ILogger logger)
    {
        var encoder = VideoEncoder.Software;
        var name = configuration["Video:Encoder"];
        if (!string.IsNullOrWhiteSpace(name) && !Enum.TryParse(name.Trim(), ignoreCase: true, out encoder))
        {
            logger.LogWarning("Unknown Video:Encoder '{Encoder}'; using software (libx264)", name);
            encoder = VideoEncoder.Software;
        }
        return new VideoTranscodeOptions(encoder, Math.Max(0, configuration.GetValue("Video:Threads", 0)));
    }

    /// <summary>
    /// H.264/AAC MP4, longest side at most <see cref="VideoPlaybackPolicy.MaxLongSide"/>,
    /// moov atom first so it starts playing (and seeking) before it has all arrived.
    /// With NVENC the GPU decodes what it can (ffmpeg falls back to the CPU for
    /// the rest) and the frames come back to the CPU for the scale.
    /// </summary>
    public string Arguments(string source, string target, bool hasAudio)
    {
        var side = VideoPlaybackPolicy.MaxLongSide;
        var scale = $"scale=w='min({side},iw)':h='min({side},ih)':force_original_aspect_ratio=decrease:force_divisible_by=2";
        var threads = Threads > 0 ? $"-threads {Threads} " : "";
        var input = Encoder == VideoEncoder.Nvenc ? "-hwaccel cuda " : "";
        var video = Encoder == VideoEncoder.Nvenc
            ? "-c:v h264_nvenc -preset p4 -tune hq -rc vbr -cq 23 -b:v 0"
            : "-c:v libx264 -preset veryfast -crf 23";
        var audio = hasAudio ? "-c:a aac -b:a 128k" : "-an";

        return
            $"{(Threads > 0 ? $"-filter_threads {Threads} " : "")}{threads}{input}-i \"{source}\" " +
            $"-map 0:v:0 {(hasAudio ? "-map 0:a:0 " : "")}-vf \"{scale}\" " +
            $"{video} -pix_fmt yuv420p -profile:v high {threads}{audio} " +
            $"-movflags +faststart -f mp4 \"{target}\"";
    }
}
