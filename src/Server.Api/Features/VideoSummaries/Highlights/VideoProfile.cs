namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// What a video looks and sounds like, one entry per second of it. Raw
/// measurements only — <see cref="HighlightFinder"/> turns them into scores, so
/// tuning the scoring never needs a video to be analysed again.
///
/// Null entries are seconds the measurement doesn't cover: a flat frame has no
/// blur reading, a video without an audio track has no loudness.
/// </summary>
/// <param name="Motion">Mean luma difference with the previous sampled frame
/// (0–255): 0 is a frozen picture, single digits a steady shot, tens a pan or a
/// shaking camera.</param>
/// <param name="Brightness">Mean luma (0–255): under ~25 is black.</param>
/// <param name="Blur">ffmpeg's blur estimate: lower is sharper.</param>
/// <param name="Loudness">Momentary loudness in LUFS: around −20 is someone
/// talking close by, under −45 is silence.</param>
/// <param name="SceneCuts">Seconds where the picture changes shot.</param>
public sealed record VideoProfile(
    double Duration,
    double?[] Motion,
    double?[] Brightness,
    double?[] Blur,
    double?[] Loudness,
    double[] SceneCuts)
{
    public int Seconds => Motion.Length;
}
