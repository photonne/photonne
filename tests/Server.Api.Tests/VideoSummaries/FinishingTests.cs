using Photonne.Server.Api.Features.VideoSummaries.Music;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// The finishing touches, without ffmpeg: the beat of a click track is found,
/// cuts move onto it within what each clip allows, and the words on screen
/// mark months or days with the place most of them were taken in.
/// </summary>
public sealed class FinishingTests
{
    /// <summary>Clicks (short 1 kHz bursts) every <paramref name="period"/> seconds.</summary>
    internal static float[] ClickTrack(double period, double seconds, double first = 0.25)
    {
        var rate = BeatTracker.SampleRate;
        var samples = new float[(int)(seconds * rate)];
        for (var t = first; t < seconds; t += period)
        {
            var at = (int)(t * rate);
            for (var i = 0; i < rate * 0.03 && at + i < samples.Length; i++)
                samples[at + i] = (float)(0.8 * Math.Sin(2 * Math.PI * 1000 * i / rate));
        }
        return samples;
    }

    [Theory]
    [InlineData(0.5)]  // 120 BPM
    [InlineData(0.6)]  // 100 BPM
    [InlineData(0.4)]  // 150 BPM
    public void TheBeat_OfAClickTrack_IsFound(double period)
    {
        var beats = BeatTracker.Track(ClickTrack(period, 30))!;

        Assert.Equal(period, beats.Period, 2);
        // Every beat lands on a click.
        Assert.All(beats.Times.Take(20), t =>
            Assert.True(Math.Abs((t - 0.25) / period - Math.Round((t - 0.25) / period)) * period < 0.03,
                $"beat at {t} is off the clicks"));
    }

    [Fact]
    public void Silence_HasNoBeat()
    {
        Assert.Null(BeatTracker.Track(new float[BeatTracker.SampleRate * 10]));
    }

    [Fact]
    public void Cuts_MoveOntoTheBeat_WithinWhatEachClipAllows()
    {
        var beats = new Beats(Enumerable.Range(0, 100).Select(i => i * 0.5).ToArray(), 0.5);
        var day = new DateTime(2024, 1, 1);
        var clips = new List<SummaryClip>
        {
            new(Guid.NewGuid(), true, day, 0, 4.2),
            new(Guid.NewGuid(), false, day, 0, 3),
            new(Guid.NewGuid(), true, day, 10, 5.1),
            new(Guid.NewGuid(), true, day, 0, 4),
        };
        // The second video has only 5.1 s left from where it starts.
        var available = new List<double> { 30, 8, 5.1, 30 };

        var aligned = BeatSync.Align(clips, available, beats, crossfade: 0.5, offset: 3);

        var start = 3.0;
        for (var i = 0; i < aligned.Count - 1; i++)
        {
            var cut = start + aligned[i].Length - 0.25;
            if (i != 2) Assert.Equal(0, cut % 0.5, 3); // on the beat
            Assert.True(aligned[i].Length <= available[i] + 1e-9);
            start += aligned[i].Length - 0.5;
        }
        Assert.Equal(4, aligned[^1].Length); // the last clip has no cut after it
    }

    private static SummaryClip Clip(DateTime at) => new(Guid.NewGuid(), true, at, 0, 4);

    [Fact]
    public void AYear_IsCaptionedByMonth_WithTheMainPlace()
    {
        var july1 = Clip(new DateTime(2024, 7, 2));
        var july2 = Clip(new DateTime(2024, 7, 20));
        var july3 = Clip(new DateTime(2024, 7, 28));
        var august = Clip(new DateTime(2024, 8, 3));
        var places = new Dictionary<Guid, string>
        {
            [july1.AssetId] = "Barcelona",
            [july2.AssetId] = "Barcelona",
            [july3.AssetId] = "Girona",
        };

        var text = SummaryChapters.Describe(new[] { july1, july2, july3, august }, VideoSummarySource.Year, places);

        Assert.Equal("Julio – agosto de 2024", text.Subtitle);
        Assert.Equal(new[] { "Julio · Barcelona", null, null, "Agosto" }, text.Captions);
    }

    [Fact]
    public void AnAlbum_IsCaptionedByDay()
    {
        var a = Clip(new DateTime(2024, 8, 12, 10, 0, 0));
        var b = Clip(new DateTime(2024, 8, 12, 18, 0, 0));
        var c = Clip(new DateTime(2024, 8, 13, 9, 0, 0));
        var places = new Dictionary<Guid, string> { [c.AssetId] = "Roma" };

        var text = SummaryChapters.Describe(new[] { a, b, c }, VideoSummarySource.Album, places);

        Assert.Equal("12 – 13 de agosto de 2024", text.Subtitle);
        Assert.Equal(new[] { "12 de agosto", null, "13 de agosto · Roma" }, text.Captions);
    }

    [Fact]
    public void OneChapter_NeedsNoCaptions()
    {
        var text = SummaryChapters.Describe(
            new[] { Clip(new DateTime(2024, 5, 1, 10, 0, 0)), Clip(new DateTime(2024, 5, 1, 12, 0, 0)) },
            VideoSummarySource.Album, new Dictionary<Guid, string>());

        Assert.Equal("1 de mayo de 2024", text.Subtitle);
        Assert.All(text.Captions, Assert.Null);
    }

    [Theory]
    [InlineData("2024-07-28", "2024-08-03", false, "28 de julio – 3 de agosto de 2024")]
    [InlineData("2023-12-30", "2024-01-02", false, "30 de diciembre de 2023 – 2 de enero de 2024")]
    [InlineData("2024-03-01", "2024-03-20", true, "Marzo de 2024")]
    public void DateRanges_ReadNaturally(string from, string to, bool byMonth, string expected)
    {
        Assert.Equal(expected, SummaryChapters.Range(DateTime.Parse(from), DateTime.Parse(to), byMonth));
    }

    [Fact]
    public void LongTitles_GetSmallerFonts()
    {
        Assert.Equal(86, SummaryRenderer.FitFontSize("Resumen 2024", 86, 1700));
        Assert.True(SummaryRenderer.FitFontSize(new string('x', 80), 86, 1700) < 40);
    }
}
